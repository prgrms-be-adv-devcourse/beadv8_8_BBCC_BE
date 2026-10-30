package com.bbcc.kidly.boundedcontext.order.in.web;

import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.OCCURRED_AT;
import static com.bbcc.kidly.boundedcontext.order.in.web.AdminOrderController.ADMIN_ORDERS_PATH;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.order.CapturedLogs;
import com.bbcc.kidly.boundedcontext.order.OrderEventDelivery;
import com.bbcc.kidly.boundedcontext.order.OrderRows;
import com.bbcc.kidly.boundedcontext.order.PaymentEvents;
import com.bbcc.kidly.boundedcontext.order.app.deadletter.OrderDeadLetterService;
import com.bbcc.kidly.boundedcontext.order.app.deadletter.dto.ReceiveDeadLetterCommand;
import com.bbcc.kidly.boundedcontext.order.app.order.OrderService;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.OrderDetail;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.PlaceOrderCommand;
import com.bbcc.kidly.boundedcontext.order.app.port.ProductReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaProduct;
import com.bbcc.kidly.boundedcontext.order.support.FunctionalTest;
import com.bbcc.kidly.boundedcontext.order.support.TestTokens;
import com.bbcc.kidly.shared.event.DomainEvent;
import com.bbcc.kidly.shared.payment.event.PaymentCompletedEvent;
import com.bbcc.kidly.shared.product.event.StockDeductedEvent;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * 관리자 주문 DLT 메시지 재처리 · 종료(#601). 재처리는 저장한 본문 · 헤더로 원래 Kafka 리스너 처리를 그대로 다시 부른다
 * (원래 토픽에 다시 넣지 않음). 행은 DLT 수신과 같은 저장 서비스로 만든다.
 */
@FunctionalTest
class AdminOrderDeadLetterActionApiTest {

    private static final String DEAD_LETTERS_PATH = ADMIN_ORDERS_PATH + "/dead-letters";
    private static final String PAYMENT_TOPIC = "kidly.payment.events";
    private static final String PAID = "ORDER PENDING_PAYMENT>PAID payment.completed";
    private static final String BODY_MARKER = "본문-표식-재처리";
    private static final Long ADMIN_ID = 99L;
    private static final Long BUYER_ID = 6_400L;
    private static final String BREAK_DELIVERY = "test_break_dead_letter_delivery_insert";
    private static final String BREAK_ORDER = "test_break_dead_letter_order_update";
    private static final String HANDED_OFF = "자체 보관으로 넘김(재처리 대기 목록에서 처리)";

    @Autowired
    private RestTestClient client;

    @Autowired
    private TestTokens tokens;

    @Autowired
    private OrderDeadLetterService deadLetterService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderEventDelivery eventDelivery;

    @Autowired
    private OrderRows orderRows;

    @Autowired
    private ProductReplicaRepository productReplicaRepository;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        productReplicaRepository.save(ReplicaProduct.create(ReplicaFixture.onSaleProduct(25_000L, OCCURRED_AT)));
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute("alter table orders.delivery drop constraint if exists " + BREAK_DELIVERY);
        jdbcTemplate.execute("alter table orders.orders drop constraint if exists " + BREAK_ORDER);
    }

    @Test
    @DisplayName("재처리 성공: 결제대기 주문의 결제 완료가 그대로 처리돼 PAID 1회분, 기록은 REPROCESSED · 처리자 · 시각, 기본 목록에서 빠진다")
    void reprocessPaymentCompleted() {
        OrderDetail order = placeAwaitingPayment();
        Long id = receive(1L, PaymentEvents.completed(order, BUYER_ID), "org.springframework.orm."
            + "ObjectOptimisticLockingFailureException");

        client.post().uri(DEAD_LETTERS_PATH + "/" + id + "/reprocess")
            .headers(headers -> headers.setBearerAuth(tokens.admin(ADMIN_ID)))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.deadLetterId").isEqualTo(String.valueOf(id))
            .jsonPath("$.status").isEqualTo("REPROCESSED")
            .jsonPath("$.handledBy").isEqualTo(String.valueOf(ADMIN_ID))
            .jsonPath("$.handledAt").exists()
            .jsonPath("$.reprocessCount").isEqualTo(1)
            .jsonPath("$.lastReprocessedAt").exists()
            .jsonPath("$.lastReprocessResult").value(value -> assertThat(value).isNull())
            .jsonPath("$.orderStatus").isEqualTo("PAID")
            .jsonPath("$.recommendedAction").value(value -> assertThat(value).isNull());

        assertThat(orderService.getOrder(BUYER_ID, order.orderId()).status()).isEqualTo("PAID");
        assertThat(orderRows.historyRows(order.orderId())).filteredOn(PAID::equals).hasSize(1);
        assertThat(deliveriesOf(order.orderId())).isEqualTo(1L);
        assertThat(openIds()).doesNotContain(String.valueOf(id));
    }

    @Test
    @DisplayName("결제 반영이 실패해 원래 처리가 주문 자체 보관(결제 반영 실패 기록)에 넘기면 REPROCESSED, "
        + "진짜 성공과 구분되게 결과에 넘김 표시, 이후는 그 재처리 대기에서 본다")
    void reprocessHandedToRecovery() {
        OrderDetail order = placeAwaitingPayment();
        Long id = receive(9L, PaymentEvents.completed(order, BUYER_ID), "java.lang.IllegalStateException");
        jdbcTemplate.execute("alter table orders.delivery add constraint " + BREAK_DELIVERY
            + " check (order_id <> " + order.orderId() + ") not valid");

        reprocess(id)
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.status").isEqualTo("REPROCESSED")
            .jsonPath("$.lastReprocessResult").isEqualTo(HANDED_OFF);

        assertThat(jdbcTemplate.queryForObject("select count(*) from orders.payment_recovery where order_id = ?",
            Long.class, order.orderId())).as("결제 반영 실패 기록").isEqualTo(1L);
        assertThat(orderService.getOrder(BUYER_ID, order.orderId()).status()).isEqualTo("PENDING_PAYMENT");
    }

    @Test
    @DisplayName("재고 차감됨 처리가 실패해 주문 자체 보관(이벤트 처리 실패 기록)에 넘기면 REPROCESSED. "
        + "재처리는 트랜잭션 없이 원래 처리를 불러야 보관이 남고 응답도 정상이다")
    void reprocessStockDeductedHandedToEventFailure() {
        OrderDetail order = orderService.place(BUYER_ID, new PlaceOrderCommand(
            List.of(new PlaceOrderCommand.Item(1L, null, 1, 25_000L)),
            new PlaceOrderCommand.ShippingAddress("홍길동", "010-0000-0000", "12345", "서울시 테스트로 1")));
        Long id = receive(10L, new StockDeductedEvent(UUID.randomUUID(), LocalDateTime.now(),
            new StockDeductedEvent.Data(order.orderId())), "java.lang.IllegalStateException", "kidly.product.events");
        jdbcTemplate.execute("alter table orders.orders add constraint " + BREAK_ORDER + " check (id <> "
            + order.orderId() + ") not valid");

        reprocess(id)
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.status").isEqualTo("REPROCESSED")
            .jsonPath("$.lastReprocessResult").isEqualTo(HANDED_OFF);

        assertThat(jdbcTemplate.queryForObject("select count(*) from orders.order_event_failure where order_id = ? "
            + "and event_type = 'STOCK_DEDUCTED'", Long.class, order.orderId())).as("이벤트 처리 실패 기록").isEqualTo(1L);
    }

    @Test
    @DisplayName("재처리(HTTP 요청 안)의 원래 처리 로그에는 요청 traceId가 그대로이고 eventId · eventType · correlationId가 붙는다(#603)")
    void reprocessLogKeepsRequestTrace() {
        OrderDetail order = placeAwaitingPayment();
        PaymentCompletedEvent completed = PaymentEvents.completed(order, BUYER_ID);
        Long id = receive(11L, completed, "java.lang.IllegalStateException");
        String requestId = UUID.randomUUID().toString();

        try (CapturedLogs logs = CapturedLogs.start()) {
            client.post().uri(DEAD_LETTERS_PATH + "/" + id + "/reprocess")
                .headers(headers -> {
                    headers.setBearerAuth(tokens.admin(ADMIN_ID));
                    headers.set("X-Request-Id", requestId);
                })
                .exchange()
                .expectStatus().isOk();

            assertThat(logs.mdcOf("결제완료: orderId=" + order.orderId()).orElseThrow())
                .containsEntry("traceId", requestId)
                .containsEntry("eventId", completed.eventId().toString())
                .containsEntry("eventType", "payment.completed")
                .containsEntry("correlationId", String.valueOf(order.orderId()));
        }
    }

    @Test
    @DisplayName("이미 처리된 이벤트(같은 결제 완료가 이미 반영됨)를 재처리하면 REPROCESSED, 주문 결과는 1회분 그대로")
    void reprocessAlreadyApplied() {
        OrderDetail order = placeAwaitingPayment();
        PaymentCompletedEvent completed = PaymentEvents.completed(order, BUYER_ID);
        eventDelivery.deliver(completed);
        long version = orderRows.versionOf(order.orderId());
        Long id = receive(2L, completed, "java.lang.IllegalStateException");

        client.post().uri(DEAD_LETTERS_PATH + "/" + id + "/reprocess")
            .headers(headers -> headers.setBearerAuth(tokens.admin(ADMIN_ID)))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.status").isEqualTo("REPROCESSED");

        assertThat(orderRows.historyRows(order.orderId())).filteredOn(PAID::equals).hasSize(1);
        assertThat(deliveriesOf(order.orderId())).isEqualTo(1L);
        assertThat(orderRows.versionOf(order.orderId())).as("주문은 바뀌지 않음").isEqualTo(version);
    }

    @Test
    @DisplayName("재처리 실패: OPEN 그대로 200, 재처리 횟수 · 마지막 시각 · 실패 이유(예외 종류만, 본문 없음), 다시 시도할 수 있다")
    void reprocessFailureStaysOpen() {
        OrderDetail order = placeAwaitingPayment();
        String body = envelope(PaymentEvents.insufficientBalance(order, BUYER_ID))
            .replace("\"INSUFFICIENT_BALANCE\"", "\"NEW_FAILURE_CODE_" + BODY_MARKER + "\"");
        Long id = receive(3L, "payment.failed", body, "tools.jackson.databind.exc.InvalidFormatException");

        for (int attempt = 1; attempt <= 2; attempt++) {
            client.post().uri(DEAD_LETTERS_PATH + "/" + id + "/reprocess")
                .headers(headers -> headers.setBearerAuth(tokens.admin(ADMIN_ID)))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("OPEN")
                .jsonPath("$.reprocessCount").isEqualTo(attempt)
                .jsonPath("$.lastReprocessedAt").exists()
                .jsonPath("$.lastReprocessResult").isEqualTo("InvalidFormatException")
                .jsonPath("$.handledBy").value(value -> assertThat(value).isNull());
        }

        assertThat(orderService.getOrder(BUYER_ID, order.orderId()).status()).isEqualTo("PENDING_PAYMENT");
        assertThat(openIds()).contains(String.valueOf(id));
        assertThat(jdbcTemplate.queryForObject("select last_reprocess_result from orders.order_dead_letter "
            + "where id = ?", String.class, id)).doesNotContain(BODY_MARKER);
    }

    @Test
    @DisplayName("원래 토픽을 모르는 메시지(DLT 헤더 없음)는 처리할 리스너가 없어 재처리 실패 · OPEN 그대로")
    void unknownSourceTopicFails() {
        deadLetterService.receive(new ReceiveDeadLetterCommand("kidly.payment.events.order.dlt", 0, 4L, "42",
            "payment.completed", "e-4", "{}", null, null, null));
        Long id = idAt(4L);

        client.post().uri(DEAD_LETTERS_PATH + "/" + id + "/reprocess")
            .headers(headers -> headers.setBearerAuth(tokens.admin(ADMIN_ID)))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.status").isEqualTo("OPEN")
            .jsonPath("$.reprocessCount").isEqualTo(1)
            .jsonPath("$.lastReprocessResult").isEqualTo("UnknownDeadLetterSourceException");
    }

    @Test
    @DisplayName("확인 후 종료: 메모와 함께 CLOSED · 처리자 · 시각, 기본 목록에서 빠지고 status=CLOSED로 보인다. 다시 종료해도 200 · 처음 메모 그대로")
    void close() {
        Long id = receive(5L, PaymentEvents.completed(placeAwaitingPayment(), BUYER_ID),
            "java.lang.IllegalStateException");

        closeWith(id, "보낸 쪽 확인함")
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.status").isEqualTo("CLOSED")
            .jsonPath("$.handledBy").isEqualTo(String.valueOf(ADMIN_ID))
            .jsonPath("$.handledAt").exists()
            .jsonPath("$.memo").isEqualTo("보낸 쪽 확인함")
            .jsonPath("$.recommendedAction").value(value -> assertThat(value).isNull());
        closeWith(id, "두 번째 메모")
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.status").isEqualTo("CLOSED")
            .jsonPath("$.memo").isEqualTo("보낸 쪽 확인함");

        assertThat(openIds()).doesNotContain(String.valueOf(id));
        assertThat(idsOf("?status=CLOSED")).containsExactly(String.valueOf(id));
    }

    @ParameterizedTest(name = "memo={0}")
    @ValueSource(strings = {"", "   "})
    @DisplayName("메모가 비어 있으면 400, 기록은 OPEN 그대로")
    void closeRequiresMemo(String memo) {
        Long id = receive(6L, PaymentEvents.completed(placeAwaitingPayment(), BUYER_ID),
            "java.lang.IllegalStateException");

        closeWith(id, memo).expectStatus().isBadRequest();
        client.post().uri(DEAD_LETTERS_PATH + "/" + id + "/close")
            .headers(headers -> headers.setBearerAuth(tokens.admin(ADMIN_ID)))
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of())
            .exchange()
            .expectStatus().isBadRequest();
        closeWith(id, "x".repeat(501)).expectStatus().isBadRequest();

        assertThat(openIds()).contains(String.valueOf(id));
    }

    @Test
    @DisplayName("이미 재처리된 건을 다시 재처리하면 아무것도 하지 않고 200 · 지금 상세(기존 재처리 대기와 같음), "
        + "종료된 건 재처리 · 재처리된 건 종료는 409")
    void alreadyHandled() {
        OrderDetail order = placeAwaitingPayment();
        Long reprocessed = receive(7L, PaymentEvents.completed(order, BUYER_ID), "java.lang.IllegalStateException");
        Long closed = receive(8L, PaymentEvents.completed(order, BUYER_ID), "java.lang.IllegalStateException");
        reprocess(reprocessed).expectStatus().isOk();
        closeWith(closed, "확인").expectStatus().isOk();
        long version = orderRows.versionOf(order.orderId());

        reprocess(reprocessed)
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.status").isEqualTo("REPROCESSED")
            .jsonPath("$.reprocessCount").isEqualTo(1);
        assertThat(orderRows.versionOf(order.orderId())).as("주문은 그대로").isEqualTo(version);
        reprocess(closed)
            .expectStatus().isEqualTo(409)
            .expectBody()
            .jsonPath("$.title").isEqualTo("이미 종료된 DLT 메시지입니다");
        closeWith(reprocessed, "확인")
            .expectStatus().isEqualTo(409)
            .expectBody()
            .jsonPath("$.title").isEqualTo("이미 재처리된 DLT 메시지입니다");
    }

    @Test
    @DisplayName("없는 ID는 재처리 · 종료 모두 404")
    void notFound() {
        reprocess(999_999L).expectStatus().isNotFound();
        closeWith(999_999L, "메모").expectStatus().isNotFound();
    }

    @Test
    @DisplayName("토큰이 없으면 401, 구매자 · 판매자 토큰이면 403(재처리 · 종료)")
    void adminOnly() {
        client.post().uri(DEAD_LETTERS_PATH + "/1/reprocess").exchange().expectStatus().isUnauthorized();
        client.post().uri(DEAD_LETTERS_PATH + "/1/close").contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("memo", "m")).exchange().expectStatus().isUnauthorized();
        for (String token : List.of(tokens.buyer(BUYER_ID), tokens.seller(BUYER_ID))) {
            client.post().uri(DEAD_LETTERS_PATH + "/1/reprocess").headers(headers -> headers.setBearerAuth(token))
                .exchange().expectStatus().isForbidden();
            client.post().uri(DEAD_LETTERS_PATH + "/1/close").headers(headers -> headers.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("memo", "m"))
                .exchange().expectStatus().isForbidden();
        }
    }

    private RestTestClient.ResponseSpec reprocess(Long id) {
        return client.post().uri(DEAD_LETTERS_PATH + "/" + id + "/reprocess")
            .headers(headers -> headers.setBearerAuth(tokens.admin(ADMIN_ID)))
            .exchange();
    }

    private RestTestClient.ResponseSpec closeWith(Long id, String memo) {
        return client.post().uri(DEAD_LETTERS_PATH + "/" + id + "/close")
            .headers(headers -> headers.setBearerAuth(tokens.admin(ADMIN_ID)))
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("memo", memo))
            .exchange();
    }

    /**
     * 결제 요청까지 간 결제대기 주문(재고 차감됨 처리됨).
     */
    private OrderDetail placeAwaitingPayment() {
        OrderDetail order = orderService.place(BUYER_ID, new PlaceOrderCommand(
            List.of(new PlaceOrderCommand.Item(1L, null, 1, 25_000L)),
            new PlaceOrderCommand.ShippingAddress("홍길동", "010-0000-0000", "12345", "서울시 테스트로 1")));
        eventDelivery.deliver(new StockDeductedEvent(UUID.randomUUID(), LocalDateTime.now(),
            new StockDeductedEvent.Data(order.orderId())));
        return order;
    }

    private Long receive(long offset, DomainEvent event, String exceptionClass) {
        return receive(offset, event, exceptionClass, PAYMENT_TOPIC);
    }

    private Long receive(long offset, DomainEvent event, String exceptionClass, String sourceTopic) {
        deadLetterService.receive(new ReceiveDeadLetterCommand(sourceTopic, 0, offset, event.correlationId(),
            event.eventType(), event.eventId().toString(), envelope(event), exceptionClass, "실패", "trace"));
        return idAt(offset);
    }

    private Long receive(long offset, String eventType, String body, String exceptionClass) {
        return receive(offset, eventType, body, exceptionClass, "42", "e-" + offset);
    }

    private Long receive(long offset, String eventType, String body, String exceptionClass, String key,
        String eventId) {
        deadLetterService.receive(new ReceiveDeadLetterCommand(PAYMENT_TOPIC, 0, offset, key, eventType, eventId,
            body, exceptionClass, "실패", "trace"));
        return idAt(offset);
    }

    private Long idAt(long offset) {
        return jdbcTemplate.queryForObject("select id from orders.order_dead_letter where source_offset = ?",
            Long.class, offset);
    }

    // 보내는 쪽 규칙의 본문: 공통 칸 7개 + data
    private String envelope(DomainEvent event) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventId", event.eventId());
        body.put("eventType", event.eventType());
        body.put("schemaVersion", event.schemaVersion());
        body.put("occurredAt", event.occurredAt());
        body.put("producer", event.producer());
        body.put("aggregateId", event.aggregateId());
        body.put("correlationId", event.correlationId());
        body.put("data", event.data());
        return jsonMapper.writeValueAsString(body);
    }

    private long deliveriesOf(Long orderId) {
        return jdbcTemplate.queryForObject("select count(*) from orders.delivery where order_id = ?", Long.class,
            orderId);
    }

    private List<String> openIds() {
        return idsOf("");
    }

    private List<String> idsOf(String query) {
        return client.get().uri(DEAD_LETTERS_PATH + query)
            .headers(headers -> headers.setBearerAuth(tokens.admin(ADMIN_ID)))
            .exchange()
            .expectStatus().isOk()
            .expectBody(Ids.class)
            .returnResult()
            .getResponseBody()
            .content().stream().map(Row::deadLetterId).toList();
    }

    private record Ids(List<Row> content) {
    }

    private record Row(String deadLetterId) {
    }
}
