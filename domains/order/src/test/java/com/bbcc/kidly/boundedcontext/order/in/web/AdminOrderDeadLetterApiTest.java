package com.bbcc.kidly.boundedcontext.order.in.web;

import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.OCCURRED_AT;
import static com.bbcc.kidly.boundedcontext.order.in.web.AdminOrderController.ADMIN_ORDERS_PATH;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.order.app.deadletter.OrderDeadLetterService;
import com.bbcc.kidly.boundedcontext.order.app.deadletter.dto.ReceiveDeadLetterCommand;
import com.bbcc.kidly.boundedcontext.order.app.order.OrderService;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.OrderDetail;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.PlaceOrderCommand;
import com.bbcc.kidly.boundedcontext.order.app.port.ProductReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaProduct;
import com.bbcc.kidly.boundedcontext.order.support.FunctionalTest;
import com.bbcc.kidly.global.security.TestMembers;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * 관리자 주문 DLT 메시지 목록 · 상세(#601). 관리자 확인 대상 원칙: 기본 목록은 사람이 조치할 OPEN만.
 * 행은 DLT 수신과 같은 저장 서비스로 만들고, 상태 · 받은 시각은 JDBC로 맞춘다(재처리 · 종료는 ④).
 */
@FunctionalTest
class AdminOrderDeadLetterApiTest {

    private static final String DEAD_LETTERS_PATH = ADMIN_ORDERS_PATH + "/dead-letters";
    private static final Long ADMIN_ID = 99L;
    private static final Long BUYER_ID = 6_300L;
    private static final String PAYMENT_TOPIC = "kidly.payment.events";
    private static final String PRODUCT_TOPIC = "kidly.product.events";
    private static final String STORE = "org.springframework.dao.DataIntegrityViolationException";
    private static final String READ = "tools.jackson.databind.exc.InvalidFormatException";
    private static final String PROCESS = "java.lang.IllegalStateException";

    @Autowired
    private RestTestClient client;

    @Autowired
    private OrderDeadLetterService deadLetterService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private ProductReplicaRepository productReplicaRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("목록: 기본은 OPEN만, 최근 받은 것부터, 칸은 ID(문자열) · 받은 시각 · 유형 · eventType · 관련 주문(없으면 null) · 횟수 · 상태")
    void listsOpenNewestFirst() {
        Long older = receive(1L, "payment.completed", "42", STORE);
        Long newer = receive(2L, "product.changed", "3", READ);
        Long closed = receive(3L, "payment.failed", "43", PROCESS);
        receivedAt(older, LocalDateTime.of(2026, 10, 31, 9, 0));
        receivedAt(newer, LocalDateTime.of(2026, 10, 31, 10, 0));
        status(closed, "CLOSED");

        client.get().uri(DEAD_LETTERS_PATH)
            .headers(TestMembers.admin(ADMIN_ID))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content.length()").isEqualTo(2)
            .jsonPath("$.content[0].deadLetterId").isEqualTo(String.valueOf(newer))
            .jsonPath("$.content[0].lastReceivedAt").isEqualTo("2026-10-31T10:00:00")
            .jsonPath("$.content[0].failureType").isEqualTo("READ_FAILED")
            .jsonPath("$.content[0].eventType").isEqualTo("product.changed")
            .jsonPath("$.content[0].orderId").value(orderId -> assertThat(orderId).isNull())
            .jsonPath("$.content[0].receivedCount").isEqualTo(1)
            .jsonPath("$.content[0].status").isEqualTo("OPEN")
            .jsonPath("$.content[1].deadLetterId").isEqualTo(String.valueOf(older))
            .jsonPath("$.content[1].orderId").isEqualTo("42")
            .jsonPath("$.content[1].failureType").isEqualTo("STORE_FAILED")
            .jsonPath("$.page").isEqualTo(0)
            .jsonPath("$.size").isEqualTo(20)
            .jsonPath("$.totalElements").isEqualTo(2)
            .jsonPath("$.totalPages").isEqualTo(1)
            .jsonPath("$.hasNext").isEqualTo(false);
    }

    @Test
    @DisplayName("목록: 결제 토픽 메시지가 먼저(돈이 걸림), 그 안과 나머지는 최근 받은 것부터. 칸 paymentEvent로 표시하고 거르기 · 쪽 나눔에도 같은 순서(#632)")
    void paymentFirst() {
        Long productNewest = receive(PRODUCT_TOPIC, 1L, "product.changed", "3", STORE);
        Long paymentOld = receive(PAYMENT_TOPIC, 2L, "payment.failed", "42", PROCESS);
        Long paymentNew = receive(PAYMENT_TOPIC, 3L, "payment.completed", "43", STORE);
        Long productOld = receive(PRODUCT_TOPIC, 4L, "stock.deducted", "44", STORE);
        receivedAt(productNewest, LocalDateTime.of(2026, 10, 31, 12, 0));
        receivedAt(paymentOld, LocalDateTime.of(2026, 10, 31, 9, 0));
        receivedAt(paymentNew, LocalDateTime.of(2026, 10, 31, 10, 0));
        receivedAt(productOld, LocalDateTime.of(2026, 10, 31, 8, 0));

        client.get().uri(DEAD_LETTERS_PATH)
            .headers(TestMembers.admin(ADMIN_ID))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content[*].deadLetterId").isEqualTo(List.of(String.valueOf(paymentNew),
                String.valueOf(paymentOld), String.valueOf(productNewest), String.valueOf(productOld)))
            .jsonPath("$.content[*].paymentEvent").isEqualTo(List.of(true, true, false, false));
        assertThat(idsOf("?size=2")).containsExactly(String.valueOf(paymentNew), String.valueOf(paymentOld));
        assertThat(idsOf("?failureType=STORE_FAILED")).containsExactly(String.valueOf(paymentNew),
            String.valueOf(productNewest), String.valueOf(productOld));
        client.get().uri(DEAD_LETTERS_PATH + "/" + paymentNew)
            .headers(TestMembers.admin(ADMIN_ID))
            .exchange()
            .expectBody()
            .jsonPath("$.paymentEvent").isEqualTo(true);
        client.get().uri(DEAD_LETTERS_PATH + "/" + productNewest)
            .headers(TestMembers.admin(ADMIN_ID))
            .exchange()
            .expectBody()
            .jsonPath("$.paymentEvent").isEqualTo(false);
    }

    @Test
    @DisplayName("상태 · 실패 유형 · eventType으로 거른다(함께 주면 모두 맞는 것만), 없으면 200 · []")
    void filters() {
        receive(1L, "payment.completed", "42", STORE);
        receive(2L, "payment.completed", "43", READ);
        Long closed = receive(3L, "payment.completed", "44", STORE);
        receive(4L, "stock.deducted", "45", STORE);
        status(closed, "CLOSED");

        assertThat(idsOf("?status=CLOSED")).containsExactly(String.valueOf(closed));
        assertThat(idsOf("?failureType=READ_FAILED")).hasSize(1);
        assertThat(idsOf("?eventType=payment.completed")).hasSize(2);
        assertThat(idsOf("?eventType=payment.completed&failureType=STORE_FAILED")).hasSize(1);
        assertThat(idsOf("?status=REPROCESSED")).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
        "status=open|지원하지 않는 DLT 메시지 상태입니다|상태는 OPEN · REPROCESSED · CLOSED 중 하나로 보내 주세요.",
        "status=PENDING|지원하지 않는 DLT 메시지 상태입니다|상태는 OPEN · REPROCESSED · CLOSED 중 하나로 보내 주세요.",
        "failureType=DB|지원하지 않는 실패 유형입니다|실패 유형은 READ_FAILED · STORE_FAILED · PROCESS_FAILED 중 하나로 보내 주세요."
    })
    @DisplayName("상태 · 실패 유형 이름이 정확히 맞지 않으면 400 ProblemDetail")
    void unknownFilterIsBadRequest(String query, String title, String detail) {
        client.get().uri(DEAD_LETTERS_PATH + "?" + query)
            .headers(TestMembers.admin(ADMIN_ID))
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.status").isEqualTo(400)
            .jsonPath("$.title").isEqualTo(title)
            .jsonPath("$.detail").isEqualTo(detail);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"size=0", "size=101", "page=-1"})
    @DisplayName("size가 1~100 밖이거나 page가 음수면 400")
    void pageOrSizeOutOfRangeIsBadRequest(String query) {
        client.get().uri(DEAD_LETTERS_PATH + "?" + query)
            .headers(TestMembers.admin(ADMIN_ID))
            .exchange()
            .expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("큰 page: offset이 int 최댓값을 넘어도 빈 목록 · 실제 건수")
    void hugePage() {
        receive(1L, "payment.completed", "42", STORE);

        client.get().uri(DEAD_LETTERS_PATH + "?page=1073741824&size=2")
            .headers(TestMembers.admin(ADMIN_ID))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content.length()").isEqualTo(0)
            .jsonPath("$.totalElements").isEqualTo(1)
            .jsonPath("$.hasNext").isEqualTo(false);
    }

    @Test
    @DisplayName("상세: 저장 칸 전체 + 지금 주문 상태(저장 안 함) + 권장 조치. ID는 문자열, 값 없는 키는 null")
    void detail() {
        OrderDetail order = place();
        Long id = receive(5L, "payment.completed", String.valueOf(order.orderId()), STORE);

        client.get().uri(DEAD_LETTERS_PATH + "/" + id)
            .headers(TestMembers.admin(ADMIN_ID))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.deadLetterId").isEqualTo(String.valueOf(id))
            .jsonPath("$.sourceTopic").isEqualTo(PAYMENT_TOPIC)
            .jsonPath("$.sourcePartition").isEqualTo(1)
            .jsonPath("$.sourceOffset").isEqualTo(5)
            .jsonPath("$.messageKey").isEqualTo(String.valueOf(order.orderId()))
            .jsonPath("$.eventType").isEqualTo("payment.completed")
            .jsonPath("$.eventId").isEqualTo("e-5")
            .jsonPath("$.payload").isEqualTo(body(String.valueOf(order.orderId())))
            .jsonPath("$.producer").isEqualTo("payment")
            .jsonPath("$.occurredAt").isEqualTo("2026-10-31T09:30:00")
            .jsonPath("$.aggregateId").isEqualTo("500")
            .jsonPath("$.correlationId").isEqualTo(String.valueOf(order.orderId()))
            .jsonPath("$.orderId").isEqualTo(String.valueOf(order.orderId()))
            .jsonPath("$.orderStatus").isEqualTo("PENDING_PAYMENT")
            .jsonPath("$.failureType").isEqualTo("STORE_FAILED")
            .jsonPath("$.exceptionClass").isEqualTo(STORE)
            .jsonPath("$.exceptionMessage").isEqualTo("실패 메시지")
            .jsonPath("$.stackTraceHead").isEqualTo("trace")
            .jsonPath("$.firstReceivedAt").exists()
            .jsonPath("$.lastReceivedAt").exists()
            .jsonPath("$.receivedCount").isEqualTo(1)
            .jsonPath("$.status").isEqualTo("OPEN")
            .jsonPath("$.handledBy").value(value -> assertThat(value).isNull())
            .jsonPath("$.handledAt").value(value -> assertThat(value).isNull())
            .jsonPath("$.memo").value(value -> assertThat(value).isNull())
            .jsonPath("$.reprocessCount").isEqualTo(0)
            .jsonPath("$.lastReprocessedAt").value(value -> assertThat(value).isNull())
            .jsonPath("$.lastReprocessResult").value(value -> assertThat(value).isNull())
            .jsonPath("$.recommendedAction").isEqualTo("결제는 됐는데 주문이 결제대기일 수 있음 — 우선 재처리");
    }

    @ParameterizedTest(name = "{0} · {1}")
    @CsvSource(delimiter = '|', value = {
        "READ_FAILED|payment.completed|보낸 도메인의 메시지 형식 · 값이 계약과 다름. 보낸 쪽 확인 뒤 재처리(고치기 전 재처리는 다시 실패)",
        "STORE_FAILED|stock.deducted|DB 상태 확인 뒤 재처리",
        "STORE_FAILED|payment.completed|결제는 됐는데 주문이 결제대기일 수 있음 — 우선 재처리",
        "PROCESS_FAILED|payment.failed|주문 현재 상태 확인 뒤 재처리 또는 종료"
    })
    @DisplayName("권장 조치는 실패 유형별 한 줄(결제 완료의 보관 실패는 결제대기 확인을 우선)")
    void recommendedActionByFailureType(String failureType, String eventType, String action) {
        String exceptionClass = switch (failureType) {
            case "READ_FAILED" -> READ;
            case "STORE_FAILED" -> STORE;
            default -> PROCESS;
        };
        Long id = receive(6L, eventType, "42", exceptionClass);

        client.get().uri(DEAD_LETTERS_PATH + "/" + id)
            .headers(TestMembers.admin(ADMIN_ID))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.failureType").isEqualTo(failureType)
            .jsonPath("$.recommendedAction").isEqualTo(action);
    }

    @Test
    @DisplayName("처리 · 종료된 건은 조치할 것이 없어 권장 조치 null, 관련 주문이 없거나 주문이 없으면 주문 상태 null")
    void nothingToDoAndNoOrder() {
        Long closed = receive(7L, "payment.completed", "42", STORE);
        status(closed, "CLOSED");
        Long product = receive(8L, "product.changed", "3", READ);

        client.get().uri(DEAD_LETTERS_PATH + "/" + closed)
            .headers(TestMembers.admin(ADMIN_ID))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.status").isEqualTo("CLOSED")
            .jsonPath("$.recommendedAction").value(value -> assertThat(value).isNull())
            .jsonPath("$.orderId").isEqualTo("42")
            .jsonPath("$.orderStatus").value(value -> assertThat(value).isNull());
        client.get().uri(DEAD_LETTERS_PATH + "/" + product)
            .headers(TestMembers.admin(ADMIN_ID))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.orderId").value(value -> assertThat(value).isNull())
            .jsonPath("$.orderStatus").value(value -> assertThat(value).isNull());
    }

    @Test
    @DisplayName("없는 ID는 404 ProblemDetail, 숫자가 아닌 ID는 400")
    void notFound() {
        client.get().uri(DEAD_LETTERS_PATH + "/999999")
            .headers(TestMembers.admin(ADMIN_ID))
            .exchange()
            .expectStatus().isNotFound()
            .expectHeader().contentType("application/problem+json")
            .expectBody()
            .jsonPath("$.title").isEqualTo("DLT 메시지를 찾을 수 없습니다");
        client.get().uri(DEAD_LETTERS_PATH + "/abc")
            .headers(TestMembers.admin(ADMIN_ID))
            .exchange()
            .expectStatus().isBadRequest();
    }

    private Long receive(long offset, String eventType, String key, String exceptionClass) {
        return receive(PAYMENT_TOPIC, offset, eventType, key, exceptionClass);
    }

    private Long receive(String topic, long offset, String eventType, String key, String exceptionClass) {
        deadLetterService.receive(new ReceiveDeadLetterCommand(topic, 1, offset, key, eventType,
            "e-" + offset, body(key), exceptionClass, "실패 메시지", "trace"));
        return jdbcTemplate.queryForObject("select id from orders.order_dead_letter where source_offset = ?",
            Long.class, offset);
    }

    private static String body(String orderId) {
        return """
            {"producer":"payment","occurredAt":"2026-10-31T09:30:00","aggregateId":"500","correlationId":"%s",
             "data":{"orderId":%s}}""".formatted(orderId, orderId);
    }

    private void receivedAt(Long id, LocalDateTime at) {
        jdbcTemplate.update("update orders.order_dead_letter set last_received_at = ? where id = ?", at, id);
    }

    private void status(Long id, String status) {
        jdbcTemplate.update("update orders.order_dead_letter set status = ? where id = ?", status, id);
    }

    private List<String> idsOf(String query) {
        return client.get().uri(DEAD_LETTERS_PATH + query)
            .headers(TestMembers.admin(ADMIN_ID))
            .exchange()
            .expectStatus().isOk()
            .expectBody(Ids.class)
            .returnResult()
            .getResponseBody()
            .content().stream().map(Row::deadLetterId).toList();
    }

    private OrderDetail place() {
        productReplicaRepository.save(ReplicaProduct.create(ReplicaFixture.onSaleProduct(25_000L, OCCURRED_AT)));
        return orderService.place(BUYER_ID, new PlaceOrderCommand(
            List.of(new PlaceOrderCommand.Item(1L, null, 1, 25_000L)),
            new PlaceOrderCommand.ShippingAddress("홍길동", "010-0000-0000", "12345", "서울시 테스트로 1")));
    }

    private record Ids(List<Row> content) {
    }

    private record Row(String deadLetterId) {
    }
}
