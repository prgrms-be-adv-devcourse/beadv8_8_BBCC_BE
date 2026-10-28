package com.bbcc.kidly.boundedcontext.order.in.web;

import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.OCCURRED_AT;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.SELLER_ID;
import static com.bbcc.kidly.boundedcontext.order.in.web.AdminOrderController.ADMIN_ORDERS_PATH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.order.app.order.OrderService;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.OrderDetail;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.PlaceOrderCommand;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderEventFailureRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.ProductReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.domain.recovery.OrderEventFailure;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaProduct;
import com.bbcc.kidly.boundedcontext.order.in.event.PaymentEventListener;
import com.bbcc.kidly.boundedcontext.order.in.event.StockEventListener;
import com.bbcc.kidly.shared.payment.domain.PaymentFailureCode;
import com.bbcc.kidly.shared.payment.event.PaymentFailedEvent;
import com.bbcc.kidly.shared.product.domain.StockDeductionFailureCode;
import com.bbcc.kidly.shared.product.event.StockDeductionFailedEvent;
import com.bbcc.kidly.support.FunctionalTest;
import com.bbcc.kidly.support.TestTokens;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * 관리자 이벤트 처리 실패 API(#449). 실패는 리스너를 직접 불러 만든다(다른 도메인 이벤트 대신).
 * 처리 실패는 그 주문 행만 막는 CHECK 제약(NOT VALID)으로 주문 UPDATE를 실패시켜 만든다.
 */
@FunctionalTest
class AdminOrderEventFailureApiTest {

    private static final String FAILURES_PATH = ADMIN_ORDERS_PATH + "/event-failures";
    private static final String BREAK = "test_break_order_update";
    private static final Long BUYER_ID = 6_200L;
    private static final Long ADMIN_ID = 99L;

    @Autowired
    private RestTestClient client;

    @Autowired
    private TestTokens tokens;

    @Autowired
    private StockEventListener stockEventListener;

    @Autowired
    private PaymentEventListener paymentEventListener;

    @Autowired
    private OrderService orderService;

    @Autowired
    private ProductReplicaRepository productReplicaRepository;

    @Autowired
    private OrderEventFailureRepository failureRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        productReplicaRepository.save(ReplicaProduct.create(ReplicaFixture.onSaleProduct(25_000L, OCCURRED_AT)));
    }

    @AfterEach
    void tearDown() {
        fixOrders();
    }

    @Test
    @DisplayName("목록: 최근 기록부터, ID는 문자열, 재고 차감 실패는 주문상품별 사유, 다른 종류는 stockFailures []")
    void listsNewestFirst() {
        OrderDetail outOfStock = place(BUYER_ID);
        failStockDeduction(outOfStock);
        OrderDetail paymentFailed = place(BUYER_ID + 1);
        failPayment(paymentFailed);

        client.get().uri(FAILURES_PATH)
            .headers(headers -> headers.setBearerAuth(tokens.admin(ADMIN_ID)))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content.length()").isEqualTo(2)
            .jsonPath("$.content[0].failureId")
            .isEqualTo(failureIdOf(paymentFailed, OrderEventFailure.EventType.PAYMENT_FAILED))
            .jsonPath("$.content[0].eventType").isEqualTo("PAYMENT_FAILED")
            .jsonPath("$.content[0].orderId").isEqualTo(String.valueOf(paymentFailed.orderId()))
            .jsonPath("$.content[0].stockFailures.length()").isEqualTo(0)
            .jsonPath("$.content[1].eventType").isEqualTo("STOCK_DEDUCTION_FAILED")
            .jsonPath("$.content[1].orderId").isEqualTo(String.valueOf(outOfStock.orderId()))
            .jsonPath("$.content[1].stockFailures.length()").isEqualTo(1)
            .jsonPath("$.content[1].stockFailures[0].orderItemId")
            .isEqualTo(String.valueOf(outOfStock.items().get(0).orderItemId()))
            .jsonPath("$.content[1].stockFailures[0].reason").isEqualTo("OUT_OF_STOCK")
            .jsonPath("$.content[1].status").isEqualTo("PENDING")
            .jsonPath("$.content[1].failureCode").isEqualTo("DataIntegrityViolationException")
            .jsonPath("$.content[1].attemptCount").isEqualTo(1)
            .jsonPath("$.content[1].lastAttemptedAt").exists()
            .jsonPath("$.content[1].createdAt").exists()
            .jsonPath("$.totalElements").isEqualTo(2);
    }

    @Test
    @DisplayName("상태로 거르면 그 상태만 나오고, 없으면 200 · []")
    void filtersByStatus() {
        failPayment(place(BUYER_ID));

        client.get().uri(FAILURES_PATH + "?status=PENDING")
            .headers(headers -> headers.setBearerAuth(tokens.admin(ADMIN_ID)))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content.length()").isEqualTo(1)
            .jsonPath("$.content[0].status").isEqualTo("PENDING");
        client.get().uri(FAILURES_PATH + "?status=RECOVERED")
            .headers(headers -> headers.setBearerAuth(tokens.admin(ADMIN_ID)))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content.length()").isEqualTo(0)
            .jsonPath("$.totalElements").isEqualTo(0);
    }

    @ParameterizedTest(name = "status={0}")
    @ValueSource(strings = {"pending", "NOT_RETRYABLE", "UNKNOWN"})
    @DisplayName("상태 이름이 정확히 맞지 않으면(소문자 · 결제 복구 전용 값 · 모르는 값) 400")
    void unknownStatusIsBadRequest(String status) {
        client.get().uri(FAILURES_PATH + "?status=" + status)
            .headers(headers -> headers.setBearerAuth(tokens.admin(ADMIN_ID)))
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.title").isEqualTo("지원하지 않는 기록 상태입니다")
            .jsonPath("$.detail").isEqualTo("상태는 PENDING · RECOVERED · CANCELLED 중 하나로 보내 주세요.");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"size=0", "size=101", "page=-1"})
    @DisplayName("size가 1~100 밖(0 · 101)이거나 page가 음수(-1)면 400")
    void pageOrSizeOutOfRangeIsBadRequest(String query) {
        client.get().uri(FAILURES_PATH + "?" + query)
            .headers(headers -> headers.setBearerAuth(tokens.admin(ADMIN_ID)))
            .exchange()
            .expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("큰 page: offset이 int 최댓값을 넘어도 빈 목록 · 실제 건수")
    void hugePage() {
        failPayment(place(BUYER_ID));

        client.get().uri(FAILURES_PATH + "?page=1073741824&size=2")
            .headers(headers -> headers.setBearerAuth(tokens.admin(ADMIN_ID)))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content.length()").isEqualTo(0)
            .jsonPath("$.totalElements").isEqualTo(1);
    }

    @Test
    @DisplayName("재처리: 원인이 남아 있으면 200 · PENDING · 시도 2, 고친 뒤면 200 · RECOVERED(재고 부족 취소), 다시 눌러도 200 · RECOVERED")
    void retry() {
        OrderDetail order = place(BUYER_ID);
        failStockDeduction(order);
        String failureId = failureIdOf(order, OrderEventFailure.EventType.STOCK_DEDUCTION_FAILED);

        post(failureId, "retry").expectStatus().isOk()
            .expectBody()
            .jsonPath("$.status").isEqualTo("PENDING")
            .jsonPath("$.attemptCount").isEqualTo(2);
        fixOrders();
        post(failureId, "retry").expectStatus().isOk().expectBody().jsonPath("$.status").isEqualTo("RECOVERED");
        post(failureId, "retry").expectStatus().isOk().expectBody().jsonPath("$.status").isEqualTo("RECOVERED");

        assertThat(orderService.getOrder(BUYER_ID, order.orderId()).cancelReason()).isEqualTo("OUT_OF_STOCK");
    }

    @Test
    @DisplayName("종료: 200 · CANCELLED, 주문은 결제대기 그대로, 다시 눌러도 200, 그 뒤 재처리는 409(이미 종료)")
    void cancel() {
        OrderDetail order = place(BUYER_ID);
        failPayment(order);
        String failureId = failureIdOf(order, OrderEventFailure.EventType.PAYMENT_FAILED);
        fixOrders();

        post(failureId, "cancel").expectStatus().isOk().expectBody().jsonPath("$.status").isEqualTo("CANCELLED");
        post(failureId, "cancel").expectStatus().isOk().expectBody().jsonPath("$.status").isEqualTo("CANCELLED");
        post(failureId, "retry").expectStatus().isEqualTo(409)
            .expectBody()
            .jsonPath("$.title").isEqualTo("이미 종료된 기록입니다");

        assertThat(orderService.getOrder(BUYER_ID, order.orderId()).status()).isEqualTo("PENDING_PAYMENT");
    }

    @Test
    @DisplayName("복구된 기록을 종료하면 409(이미 종료), 없는 기록은 재처리 · 종료 모두 404")
    void recoveredAndMissing() {
        OrderDetail order = place(BUYER_ID);
        failPayment(order);
        String failureId = failureIdOf(order, OrderEventFailure.EventType.PAYMENT_FAILED);
        fixOrders();
        post(failureId, "retry").expectStatus().isOk();

        post(failureId, "cancel").expectStatus().isEqualTo(409)
            .expectBody()
            .jsonPath("$.title").isEqualTo("이미 종료된 기록입니다")
            .jsonPath("$.detail").isEqualTo("이미 복구됐거나 종료된 기록입니다. 이벤트 처리 실패 목록을 새로고침해 주세요.");
        for (String action : List.of("retry", "cancel")) {
            post("999999", action).expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.title").isEqualTo("이벤트 처리 실패 기록을 찾을 수 없습니다");
        }
    }

    @Test
    @DisplayName("토큰이 없으면 401, 구매자 · 판매자 토큰이면 403(목록 · 재처리 · 종료)")
    void adminOnly() {
        client.get().uri(FAILURES_PATH).exchange().expectStatus().isUnauthorized();
        client.post().uri(FAILURES_PATH + "/1/retry").exchange().expectStatus().isUnauthorized();
        for (String token : List.of(tokens.buyer(BUYER_ID), tokens.seller(SELLER_ID))) {
            client.get().uri(FAILURES_PATH)
                .headers(headers -> headers.setBearerAuth(token))
                .exchange().expectStatus().isForbidden();
            client.post().uri(FAILURES_PATH + "/1/retry")
                .headers(headers -> headers.setBearerAuth(token))
                .exchange().expectStatus().isForbidden();
            client.post().uri(FAILURES_PATH + "/1/cancel")
                .headers(headers -> headers.setBearerAuth(token))
                .exchange().expectStatus().isForbidden();
        }
    }

    private RestTestClient.ResponseSpec post(String failureId, String action) {
        return client.post().uri(FAILURES_PATH + "/" + failureId + "/" + action)
            .headers(headers -> headers.setBearerAuth(tokens.admin(ADMIN_ID)))
            .exchange();
    }

    private OrderDetail place(Long buyerId) {
        return orderService.place(buyerId, new PlaceOrderCommand(
            List.of(new PlaceOrderCommand.Item(1L, null, 1, 25_000L)),
            new PlaceOrderCommand.ShippingAddress("홍길동", "010-0000-0000", "12345", "서울시 테스트로 1")));
    }

    private void failStockDeduction(OrderDetail order) {
        breakOrder(order);
        assertThatThrownBy(() -> stockEventListener.onStockDeductionFailed(new StockDeductionFailedEvent(
            UUID.randomUUID(), LocalDateTime.now(), new StockDeductionFailedEvent.Data(order.orderId(),
            StockDeductionFailureCode.OUT_OF_STOCK, List.of(new StockDeductionFailedEvent.Failure(
                order.items().get(0).orderItemId(), StockDeductionFailureCode.OUT_OF_STOCK))))))
            .isInstanceOf(RuntimeException.class);
    }

    private void failPayment(OrderDetail order) {
        breakOrder(order);
        LocalDateTime now = LocalDateTime.now();
        assertThatThrownBy(() -> paymentEventListener.onPaymentFailed(new PaymentFailedEvent(UUID.randomUUID(), now,
            new PaymentFailedEvent.Data(null, order.orderId(), BUYER_ID, order.totalAmount(),
                PaymentFailureCode.WALLET_NOT_FOUND, now)))).isInstanceOf(RuntimeException.class);
    }

    // NOT VALID라 기존 행 검사 없이 걸린다. 그 주문 행을 바꾸는 UPDATE만 실패한다
    private void breakOrder(OrderDetail order) {
        fixOrders();
        jdbcTemplate.execute("alter table orders.orders add constraint " + BREAK + " check (id <> "
            + order.orderId() + ") not valid");
    }

    private void fixOrders() {
        jdbcTemplate.execute("alter table orders.orders drop constraint if exists " + BREAK);
    }

    private String failureIdOf(OrderDetail order, OrderEventFailure.EventType eventType) {
        return String.valueOf(failureRepository.findByOrderIdAndEventType(order.orderId(), eventType)
            .orElseThrow().getId());
    }
}
