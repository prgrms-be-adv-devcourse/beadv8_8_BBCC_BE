package com.bbcc.kidly.boundedcontext.order.in.mcp;

import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.BUYER_ID;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.OCCURRED_AT;
import static com.bbcc.kidly.boundedcontext.order.in.config.OrderMcpConfig.MCP_PATH;
import static com.bbcc.kidly.boundedcontext.order.in.web.OrderController.ORDERS_PATH;
import static com.bbcc.kidly.boundedcontext.order.in.web.admin.AdminOrderController.ADMIN_ORDERS_PATH;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.order.OrderEventDelivery;
import com.bbcc.kidly.boundedcontext.order.PaymentEvents;
import com.bbcc.kidly.boundedcontext.order.app.deadletter.OrderDeadLetterService;
import com.bbcc.kidly.boundedcontext.order.app.deadletter.dto.ReceiveDeadLetterCommand;
import com.bbcc.kidly.boundedcontext.order.app.order.OrderQueryService;
import com.bbcc.kidly.boundedcontext.order.app.order.OrderService;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.OrderDetail;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.PlaceOrderCommand;
import com.bbcc.kidly.boundedcontext.order.app.port.ChildReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderEventFailureRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderOutboxRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.PaymentRecoveryRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.ProductReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.domain.recovery.OrderEventFailure;
import com.bbcc.kidly.boundedcontext.order.domain.recovery.PaymentRecovery;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaChild;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaProduct;
import com.bbcc.kidly.boundedcontext.support.FunctionalTest;
import com.bbcc.kidly.global.security.TestAdmins;
import com.bbcc.kidly.global.security.TestMembers;
import com.bbcc.kidly.shared.product.event.StockDeductedEvent;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 주문 운영 조회 MCP 창구(#925): POST /api/v1/admin/orders/mcp(JSON-RPC, 상태 없음). 주문 모듈 테스트는 스위치를 켜고
 * 상한(max-results)을 3으로 둔다(application-test.yml). 꺼지면 경로가 없는 것은 OrderMcpConfigTest.
 */
@FunctionalTest
class OrderMcpApiTest {

    private static final Long ADMIN_ID = 99L;
    private static final int MAX_RESULTS = 3;
    private static final String RECIPIENT = "김수령";
    private static final String PHONE = "010-9876-5432";
    private static final String ADDRESS = "서울시 비밀구 비밀로 77";
    private static final List<String> TOOLS = List.of("stuck_orders_summary", "stuck_orders", "order_timeline",
        "dead_letters", "dead_letter_detail", "event_failures", "payment_recoveries", "outbox_quarantined");
    // 응답에 나오면 안 되는 개인정보 칸 이름(배송지 · 연락처 · 수령인 · 닉네임 · 자녀 정보 · 원본 본문 · 관리자 메모)
    private static final List<String> PRIVATE_FIELDS = List.of("\"recipientName\"", "\"phone\"", "\"zipCode\"",
        "\"address\"", "\"shippingAddress\"", "\"nickname\"", "\"childName\"", "\"height\"", "\"weight\"",
        "\"payload\"", "\"memo\"", "\"exceptionMessage\"", "\"stackTraceHead\"");

    @Autowired
    private RestTestClient client;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderQueryService orderQueries;

    @Autowired
    private OrderEventDelivery eventDelivery;

    @Autowired
    private OrderDeadLetterService deadLetterService;

    @Autowired
    private OrderEventFailureRepository failureRepository;

    @Autowired
    private PaymentRecoveryRepository recoveryRepository;

    @Autowired
    private OrderOutboxRepository outboxRepository;

    @Autowired
    private ProductReplicaRepository productReplicaRepository;

    @Autowired
    private ChildReplicaRepository childReplicaRepository;

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JsonMapper jsonMapper;

    @BeforeEach
    void setUp() {
        productReplicaRepository.save(ReplicaProduct.create(ReplicaFixture.onSaleProduct(25_000L, OCCURRED_AT)));
        childReplicaRepository.save(ReplicaChild.create(ReplicaFixture.child("115.0", false, OCCURRED_AT)));
    }

    @Test
    @DisplayName("도구 목록은 읽기 전용 조회 도구 8개뿐(조작 도구 없음), 모두 readOnlyHint true · destructiveHint false")
    void listsReadOnlyToolsOnly() {
        JsonNode result = rpc(TestAdmins.of(ADMIN_ID), "tools/list", Map.of());

        List<String> names = new ArrayList<>();
        result.get("tools").forEach(tool -> {
            names.add(tool.get("name").asString());
            assertThat(tool.get("annotations").get("readOnlyHint").asBoolean()).isTrue();
            assertThat(tool.get("annotations").get("destructiveHint").asBoolean()).isFalse();
        });
        assertThat(names).containsExactlyInAnyOrderElementsOf(TOOLS);
    }

    @Test
    @DisplayName("각 도구를 부르면 관리자 조회 API와 같은 값을 돌려주고, 어떤 응답에도 배송지 · 연락처 · 수령인 · 자녀 정보 · DLT 본문이 없다")
    void everyToolAnswersWithoutPersonalData() {
        OrderDetail paid = paidOrder();
        OrderDetail stale = place();
        jdbcTemplate.update("update orders.orders set created_at = ?, payment_due_at = ? where id = ?",
            LocalDateTime.now().minusHours(1), LocalDateTime.now().plusDays(1), stale.orderId());
        Long deadLetterId = deadLetter(1L, paid.orderId());
        jdbcTemplate.update("update orders.order_dead_letter set memo = ? where id = ?", RECIPIENT + " 확인",
            deadLetterId);
        failureRepository.save(OrderEventFailure.open(OrderEventFailure.EventType.PAYMENT_FAILED, stale.orderId(),
            List.of(), "WALLET_NOT_FOUND", LocalDateTime.now()));
        recoveryRepository.save(PaymentRecovery.open(paid.orderId(), 500L, BUYER_ID, paid.totalAmount(),
            List.of(new PaymentRecovery.PaidItem(paid.items().getFirst().orderItemId(), paid.totalAmount())),
            "member-mismatch", true, LocalDateTime.now()));
        Long outboxId = jdbcTemplate.queryForObject("select min(id) from orders.order_outbox where message_key = ?",
            Long.class, String.valueOf(stale.orderId()));
        transactions.executeWithoutResult(status -> outboxRepository.quarantine(outboxId, "RecordTooLargeException",
            LocalDateTime.now()));

        List<String> texts = new ArrayList<>();
        JsonNode summary = callOk("stuck_orders_summary", Map.of(), texts);
        assertThat(summary.get("STOCK_CHECK_STALE").asLong()).isEqualTo(1);
        JsonNode stuck = callOk("stuck_orders", Map.of("kind", "STOCK_CHECK_STALE"), texts);
        assertThat(stuck.get("items").get(0).get("orderId").asString()).isEqualTo(String.valueOf(stale.orderId()));
        JsonNode timeline = callOk("order_timeline", Map.of("orderId", String.valueOf(paid.orderId())), texts);
        assertThat(timeline.get("orderId").asString()).isEqualTo(String.valueOf(paid.orderId()));
        JsonNode deadLetters = callOk("dead_letters", Map.of(), texts);
        assertThat(deadLetters.get("items").get(0).get("deadLetterId").asString())
            .isEqualTo(String.valueOf(deadLetterId));
        JsonNode detail = callOk("dead_letter_detail", Map.of("deadLetterId", deadLetterId), texts);
        assertThat(detail.get("eventType").asString()).isEqualTo("payment.completed");
        assertThat(detail.get("orderId").asString()).isEqualTo(String.valueOf(paid.orderId()));
        assertThat(detail.get("orderStatus").asString()).isEqualTo("PAID");
        assertThat(detail.get("exceptionClass").asString()).isEqualTo("java.lang.IllegalStateException");
        assertThat(detail.has("exceptionMessage")).isFalse();
        assertThat(detail.has("stackTraceHead")).isFalse();
        assertThat(detail.has("payload")).isFalse();
        assertThat(detail.has("memo")).isFalse();
        assertThat(detail.get("lastReceivedAt").asString()).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*");
        JsonNode failures = callOk("event_failures", Map.of("status", "PENDING"), texts);
        assertThat(failures.get("items").get(0).get("eventType").asString()).isEqualTo("PAYMENT_FAILED");
        JsonNode recoveries = callOk("payment_recoveries", Map.of(), texts);
        assertThat(recoveries.get("items").get(0).get("failureCode").asString()).isEqualTo("member-mismatch");
        JsonNode quarantined = callOk("outbox_quarantined", Map.of(), texts);
        assertThat(quarantined.get("items").get(0).get("outboxId").asString()).isEqualTo(String.valueOf(outboxId));
        assertThat(quarantined.get("items").get(0).get("status").asString()).isEqualTo("QUARANTINED");

        assertThat(texts).hasSize(TOOLS.size()).allSatisfy(text -> {
            assertThat(text).doesNotContain(RECIPIENT, PHONE, ADDRESS);
            PRIVATE_FIELDS.forEach(field -> assertThat(text).doesNotContain(field));
        });
    }

    @Test
    @DisplayName("자유 문구 칸(아웃박스 lastError · 이력 reason/source · 실패 코드 · 재고 사유 · DLT 키/ID/예외 종류)에 개인정보를 넣어도 "
        + "MCP 응답에는 코드 · 예외 클래스 이름 · 경로 틀 · 이벤트 타입 · ID 형식만 나가고 그 문구는 없다")
    void freeTextFieldsAreFiltered() {
        OrderDetail paid = paidOrder();
        OrderDetail stale = place();
        jdbcTemplate.update("update orders.orders set created_at = ?, payment_due_at = ? where id = ?",
            LocalDateTime.now().minusHours(1), LocalDateTime.now().plusDays(1), stale.orderId());
        Long outboxId = jdbcTemplate.queryForObject("select min(id) from orders.order_outbox where message_key = ?",
            Long.class, String.valueOf(stale.orderId()));
        transactions.executeWithoutResult(status -> outboxRepository.quarantine(outboxId,
            "RecordTooLargeException: " + RECIPIENT + " " + PHONE + " " + ADDRESS, LocalDateTime.now()));
        jdbcTemplate.update("insert into orders.order_status_history (order_id, target_type, target_id, from_status,"
                + " to_status, reason, actor_type, actor_id, source, occurred_at, created_at, updated_at)"
                + " values (?, 'ORDER', ?, 'PAID', 'PAID', ?, 'ADMIN', 99, ?, now(), now(), now())",
            paid.orderId(), paid.orderId(), RECIPIENT + " 요청으로 확인", "전화 " + PHONE);
        failureRepository.save(OrderEventFailure.open(OrderEventFailure.EventType.STOCK_DEDUCTION_FAILED,
            stale.orderId(), List.of(new OrderEventFailure.StockFailure(stale.items().getFirst().orderItemId(),
                RECIPIENT)), "연락 " + PHONE, LocalDateTime.now()));
        recoveryRepository.save(PaymentRecovery.open(paid.orderId(), 500L, BUYER_ID, paid.totalAmount(),
            List.of(new PaymentRecovery.PaidItem(paid.items().getFirst().orderItemId(), paid.totalAmount())),
            ADDRESS, true, LocalDateTime.now()));
        // 키는 전화번호 형태, aggregateId는 주민번호 형태, correlationId는 관련 주문 ID(이것만 나간다)
        String received = """
            {"producer":"payment","occurredAt":"2026-10-31T09:30:00","aggregateId":"9001011234567",
             "correlationId":"%s","data":{"orderId":%s}}""".formatted(paid.orderId(), paid.orderId());
        deadLetterService.receive(new ReceiveDeadLetterCommand("kidly.payment.events", 1, 7L, "01098765432",
            "payment.completed", RECIPIENT, received, "IllegalStateException: " + ADDRESS, "실패", "trace"));
        Long deadLetterId = jdbcTemplate.queryForObject(
            "select id from orders.order_dead_letter where source_offset = 7", Long.class);
        // 토픽이 kidly.{x}.events 형식이 아니면 producer · eventType 모두 null. 재처리 결과는 '넘김'이면 HANDED_OFF
        deadLetterService.receive(new ReceiveDeadLetterCommand("payment-legacy", 1, 8L, null, "payment.completed",
            "3f2504e0-4f89-11d3-9a0c-0305e82c3301", "{\"producer\":\"payment\",\"aggregateId\":\"john.smith\"}",
            "java.lang.IllegalStateException", "실패", "trace"));
        Long otherTopicId = jdbcTemplate.queryForObject(
            "select id from orders.order_dead_letter where source_offset = 8", Long.class);
        jdbcTemplate.update("update orders.order_dead_letter set last_reprocess_result = ? where id = ?",
            "자체 보관으로 넘김(재처리 대기 목록에서 처리)", deadLetterId);
        jdbcTemplate.update("update orders.order_dead_letter set last_reprocess_result = ? where id = ?",
            "IllegalStateException", otherTopicId);

        List<String> texts = new ArrayList<>();
        JsonNode quarantined = callOk("outbox_quarantined", Map.of(), texts);
        assertThat(quarantined.get("items").get(0).get("lastError").asString()).isEqualTo("RecordTooLargeException");
        JsonNode stuck = callOk("stuck_orders", Map.of("kind", "OUTBOX_STUCK"), texts);
        assertThat(stuck.get("items").get(0).get("outbox").get("lastError").asString())
            .isEqualTo("RecordTooLargeException");
        JsonNode timeline = callOk("order_timeline", Map.of("orderId", paid.orderId()), texts);
        JsonNode last = timeline.get("history").get(timeline.get("history").size() - 1);
        assertThat(last.get("reason").isNull()).isTrue();
        assertThat(last.get("source").isNull()).isTrue();
        JsonNode failures = callOk("event_failures", Map.of(), texts);
        assertThat(failures.get("items").get(0).get("failureCode").isNull()).isTrue();
        assertThat(failures.get("items").get(0).get("stockFailures").get(0).get("reason").isNull()).isTrue();
        JsonNode recoveries = callOk("payment_recoveries", Map.of(), texts);
        assertThat(recoveries.get("items").get(0).get("failureCode").isNull()).isTrue();
        JsonNode deadLetters = callOk("dead_letters", Map.of(), texts);
        Map<String, String> eventTypes = new java.util.HashMap<>();
        deadLetters.get("items").forEach(row -> eventTypes.put(row.get("deadLetterId").asString(),
            row.get("eventType").isNull() ? null : row.get("eventType").asString()));
        assertThat(eventTypes).containsEntry(String.valueOf(deadLetterId), "payment.completed")
            .containsEntry(String.valueOf(otherTopicId), null);
        JsonNode detail = callOk("dead_letter_detail", Map.of("deadLetterId", deadLetterId), texts);
        assertThat(detail.get("exceptionClass").asString()).isEqualTo("IllegalStateException");
        assertThat(detail.get("messageKey").isNull()).isTrue();
        assertThat(detail.get("aggregateId").isNull()).isTrue();
        assertThat(detail.get("correlationId").asString()).isEqualTo(String.valueOf(paid.orderId()));
        assertThat(detail.get("producer").asString()).isEqualTo("payment");
        assertThat(detail.get("eventType").asString()).isEqualTo("payment.completed");
        assertThat(detail.get("eventId").isNull()).isTrue();
        assertThat(detail.get("lastReprocessResult").asString()).isEqualTo("HANDED_OFF");
        JsonNode other = callOk("dead_letter_detail", Map.of("deadLetterId", otherTopicId), texts);
        assertThat(other.get("producer").isNull()).isTrue();
        assertThat(other.get("eventType").isNull()).isTrue();
        assertThat(other.get("aggregateId").isNull()).isTrue();
        assertThat(other.get("eventId").asString()).isEqualTo("3f2504e0-4f89-11d3-9a0c-0305e82c3301");
        assertThat(other.get("exceptionClass").asString()).isEqualTo("java.lang.IllegalStateException");
        assertThat(other.get("lastReprocessResult").asString()).isEqualTo("IllegalStateException");

        assertThat(texts).allSatisfy(text -> assertThat(text).doesNotContain(RECIPIENT, PHONE, ADDRESS, "전화",
            "연락", "요청으로", "01098765432", "9001011234567", "john.smith", "자체 보관"));
    }

    @Test
    @DisplayName("목록은 size를 max-results로 줄이고(요청 10 → 3, 전체 수 · 다음 페이지 있음), 이력이 길면 최근 max-results줄만(truncated)")
    void limitsResults() {
        for (int i = 0; i < MAX_RESULTS + 1; i++) {
            failureRepository.save(OrderEventFailure.open(OrderEventFailure.EventType.PAYMENT_FAILED, place().orderId(),
                List.of(), "WALLET_NOT_FOUND", LocalDateTime.now()));
        }
        OrderDetail paid = paidOrder();
        client.post().uri(ORDERS_PATH + "/" + paid.orderId() + "/cancel")
            .headers(TestMembers.buyer(BUYER_ID))
            .exchange()
            .expectStatus().isOk();

        JsonNode failures = callOk("event_failures", Map.of("size", 10), new ArrayList<>());
        assertThat(failures.get("items").size()).isEqualTo(MAX_RESULTS);
        assertThat(failures.get("size").asInt()).isEqualTo(MAX_RESULTS);
        assertThat(failures.get("totalElements").asLong()).isEqualTo(MAX_RESULTS + 1);
        assertThat(failures.get("hasNext").asBoolean()).isTrue();

        JsonNode timeline = callOk("order_timeline", Map.of("orderId", paid.orderId()), new ArrayList<>());
        List<String> allIds = adminHistoryIds(paid.orderId());
        assertThat(allIds).hasSizeGreaterThan(MAX_RESULTS);
        assertThat(timeline.get("totalCount").asInt()).isEqualTo(allIds.size());
        assertThat(timeline.get("truncated").asBoolean()).isTrue();
        List<String> ids = new ArrayList<>();
        timeline.get("history").forEach(row -> ids.add(row.get("historyId").asString()));
        assertThat(ids).isEqualTo(allIds.subList(allIds.size() - MAX_RESULTS, allIds.size()));
    }

    @Test
    @DisplayName("업무 오류 · 잘못된 인자는 도구 오류(isError)로 답한다: 없는 주문 ORDER_NOT_FOUND, 모르는 멈춤 종류, 0 이하 ID")
    void toolErrors() {
        JsonNode notFound = call(TestAdmins.of(ADMIN_ID), "order_timeline", Map.of("orderId", "987654321"));
        assertThat(notFound.get("isError").asBoolean()).isTrue();
        assertThat(jsonMapper.readTree(textOf(notFound)).get("error").asString()).isEqualTo("ORDER_NOT_FOUND");

        assertThat(call(TestAdmins.of(ADMIN_ID), "dead_letter_detail", Map.of("deadLetterId", "0"))
            .get("isError").asBoolean()).isTrue();
        assertThat(call(TestAdmins.of(ADMIN_ID), "event_failures", Map.of("status", "UNKNOWN"))
            .get("isError").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("관리자 헤더가 없거나 숫자가 아니거나 0이면 401 ProblemDetail이고 MCP로 넘기지 않는다")
    void rejectsWithoutAdmin() {
        List<Consumer<HttpHeaders>> invalid = List.of(headers -> { }, headers -> headers.set("X-Admin-Id", "abc"),
            headers -> headers.set("X-Admin-Id", "0"), TestMembers.buyer(BUYER_ID));
        invalid.forEach(headers -> client.post().uri(MCP_PATH)
            .headers(headers)
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
            .body(request("tools/list", Map.of()))
            .exchange()
            .expectStatus().isUnauthorized()
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.status").isEqualTo(401)
            .jsonPath("$.instance").isEqualTo(MCP_PATH));
    }

    private JsonNode callOk(String tool, Map<String, Object> arguments, List<String> texts) {
        JsonNode result = call(TestAdmins.of(ADMIN_ID), tool, arguments);
        assertThat(result.get("isError").asBoolean()).as(tool + " " + result).isFalse();
        String text = textOf(result);
        texts.add(text);
        return jsonMapper.readTree(text);
    }

    private JsonNode call(Consumer<HttpHeaders> headers, String tool, Map<String, Object> arguments) {
        return rpc(headers, "tools/call", Map.of("name", tool, "arguments", arguments));
    }

    private JsonNode rpc(Consumer<HttpHeaders> headers, String method, Map<String, Object> params) {
        String body = client.post().uri(MCP_PATH)
            .headers(headers)
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
            .body(request(method, params))
            .exchange()
            .expectStatus().isOk()
            .expectBody(String.class)
            .returnResult()
            .getResponseBody();
        JsonNode response = jsonMapper.readTree(body);
        assertThat(response.has("error")).as(body).isFalse();
        return response.get("result");
    }

    private static Map<String, Object> request(String method, Map<String, Object> params) {
        return Map.of("jsonrpc", "2.0", "id", 1, "method", method, "params", params);
    }

    private static String textOf(JsonNode result) {
        return result.get("content").get(0).get("text").asString();
    }

    private List<String> adminHistoryIds(Long orderId) {
        JsonNode rows = jsonMapper.readTree(client.get().uri(ADMIN_ORDERS_PATH + "/" + orderId + "/history")
            .headers(TestAdmins.of(ADMIN_ID))
            .exchange()
            .expectStatus().isOk()
            .expectBody(String.class)
            .returnResult()
            .getResponseBody());
        List<String> ids = new ArrayList<>();
        rows.forEach(row -> ids.add(row.get("historyId").asString()));
        return ids;
    }

    private OrderDetail place() {
        return orderService.place(BUYER_ID, new PlaceOrderCommand(
            List.of(new PlaceOrderCommand.Item(1L, 1L, 1, 25_000L)),
            new PlaceOrderCommand.ShippingAddress(RECIPIENT, PHONE, "12345", ADDRESS)));
    }

    private OrderDetail paidOrder() {
        OrderDetail order = place();
        eventDelivery.deliver(new StockDeductedEvent(UUID.randomUUID(), LocalDateTime.now(),
            new StockDeductedEvent.Data(order.orderId())));
        eventDelivery.deliver(PaymentEvents.completed(orderQueries.getOrder(BUYER_ID, order.orderId()), BUYER_ID));
        return order;
    }

    // DLT 본문에 개인정보가 들어 있어도 MCP 응답에는 본문이 나가지 않는다
    private Long deadLetter(long offset, Long orderId) {
        String body = """
            {"producer":"payment","occurredAt":"2026-10-31T09:30:00","aggregateId":"500","correlationId":"%s",
             "data":{"orderId":%s,"recipientName":"%s","phone":"%s","address":"%s"}}"""
            .formatted(orderId, orderId, RECIPIENT, PHONE, ADDRESS);
        deadLetterService.receive(new ReceiveDeadLetterCommand("kidly.payment.events", 1, offset,
            String.valueOf(orderId), "payment.completed", "e-" + offset, body,
            "java.lang.IllegalStateException", "실패 " + RECIPIENT + " " + PHONE, "trace " + ADDRESS));
        return jdbcTemplate.queryForObject("select id from orders.order_dead_letter where source_offset = ?",
            Long.class, offset);
    }
}
