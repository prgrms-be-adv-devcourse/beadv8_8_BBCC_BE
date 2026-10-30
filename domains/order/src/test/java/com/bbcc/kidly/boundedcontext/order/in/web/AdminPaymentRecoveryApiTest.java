package com.bbcc.kidly.boundedcontext.order.in.web;

import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.OCCURRED_AT;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.SELLER_ID;
import static com.bbcc.kidly.boundedcontext.order.in.web.AdminOrderController.ADMIN_ORDERS_PATH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.order.OrderEventDelivery;
import com.bbcc.kidly.boundedcontext.order.app.cart.CartService;
import com.bbcc.kidly.boundedcontext.order.app.order.OrderService;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.OrderDetail;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.PlaceOrderCommand;
import com.bbcc.kidly.boundedcontext.order.app.port.PaymentRecoveryRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.ProductReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaProduct;
import com.bbcc.kidly.shared.payment.event.PaymentCompletedEvent;
import com.bbcc.kidly.boundedcontext.order.support.FunctionalTest;
import com.bbcc.kidly.boundedcontext.order.support.TestTokens;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * 관리자 결제 반영 실패 API(#353). 실패는 결제 완료 리스너를 직접 불러 만든다(다른 도메인 이벤트 대신).
 * 배송 생성 실패는 한 주문상품의 seller_id를 NULL로 바꿔 만든다(delivery.seller_id NOT NULL 위반, 진 쪽 아님).
 */
@FunctionalTest
class AdminPaymentRecoveryApiTest {

    private static final String RECOVERIES_PATH = ADMIN_ORDERS_PATH + "/recoveries";
    private static final Long BUYER_ID = 6_000L;
    private static final Long OTHER_SELLER_ID = 3L;
    private static final Long ADMIN_ID = 99L;
    private static final Long PAYMENT_ID = 500L;

    @Autowired
    private OrderEventDelivery eventDelivery;

    @Autowired
    private RestTestClient client;

    @Autowired
    private TestTokens tokens;

    @Autowired
    private OrderService orderService;

    @Autowired
    private CartService cartService;

    @Autowired
    private ProductReplicaRepository productReplicaRepository;

    @Autowired
    private PaymentRecoveryRepository recoveryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        productReplicaRepository.save(ReplicaProduct.create(ReplicaFixture.onSaleProduct(25_000L, OCCURRED_AT)));
        productReplicaRepository.save(ReplicaProduct.create(new ReplicaProduct.Values(
            2L, 2L, OTHER_SELLER_ID, "상품 B", null, "120", 30_000L, 2L, "ON_SALE", null, false, OCCURRED_AT)));
    }

    @Test
    @DisplayName("목록: 최근 기록부터, ID는 문자열, 결제 쪽이 빠뜨린 결제 ID · 회원은 null 키로 나온다")
    void listsNewestFirst() {
        OrderDetail pending = failDelivery(BUYER_ID);
        OrderDetail notRetryable = place(BUYER_ID + 1);
        failWith(notRetryable, null, null);

        client.get().uri(RECOVERIES_PATH)
            .headers(headers -> headers.setBearerAuth(tokens.admin(ADMIN_ID)))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content.length()").isEqualTo(2)
            .jsonPath("$.content[0].recoveryId").isEqualTo(recoveryIdOf(notRetryable))
            .jsonPath("$.content[0].orderId").isEqualTo(String.valueOf(notRetryable.orderId()))
            .jsonPath("$.content[0].status").isEqualTo("NOT_RETRYABLE")
            .jsonPath("$.content[0].failureCode").isEqualTo("member-mismatch")
            .jsonPath("$.content[0].paymentId").value(value -> assertThat(value).isNull())
            .jsonPath("$.content[0].memberId").value(value -> assertThat(value).isNull())
            .jsonPath("$.content[1].orderId").isEqualTo(String.valueOf(pending.orderId()))
            .jsonPath("$.content[1].paymentId").isEqualTo(String.valueOf(PAYMENT_ID))
            .jsonPath("$.content[1].memberId").isEqualTo(String.valueOf(BUYER_ID))
            .jsonPath("$.content[1].amount").isEqualTo(55_000)
            .jsonPath("$.content[1].items.length()").isEqualTo(2)
            .jsonPath("$.content[1].items[0].orderItemId")
            .isEqualTo(String.valueOf(pending.items().get(0).orderItemId()))
            .jsonPath("$.content[1].items[0].amount").isEqualTo(25_000)
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
        failDelivery(BUYER_ID);

        client.get().uri(RECOVERIES_PATH + "?status=PENDING")
            .headers(headers -> headers.setBearerAuth(tokens.admin(ADMIN_ID)))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content.length()").isEqualTo(1)
            .jsonPath("$.content[0].status").isEqualTo("PENDING");
        client.get().uri(RECOVERIES_PATH + "?status=RECOVERED")
            .headers(headers -> headers.setBearerAuth(tokens.admin(ADMIN_ID)))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content.length()").isEqualTo(0)
            .jsonPath("$.totalElements").isEqualTo(0);
    }

    @ParameterizedTest(name = "status={0}")
    @ValueSource(strings = {"pending", "UNKNOWN"})
    @DisplayName("상태 이름이 정확히 맞지 않으면(소문자 · 모르는 값) 400")
    void unknownStatusIsBadRequest(String status) {
        client.get().uri(RECOVERIES_PATH + "?status=" + status)
            .headers(headers -> headers.setBearerAuth(tokens.admin(ADMIN_ID)))
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.title").isEqualTo("지원하지 않는 기록 상태입니다")
            .jsonPath("$.detail").isEqualTo("상태는 PENDING · RECOVERED · CANCELLED · NOT_RETRYABLE 중 하나로 보내 주세요.");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"size=0", "size=101", "page=-1"})
    @DisplayName("size가 1~100 밖(0 · 101)이거나 page가 음수(-1)면 400")
    void pageOrSizeOutOfRangeIsBadRequest(String query) {
        client.get().uri(RECOVERIES_PATH + "?" + query)
            .headers(headers -> headers.setBearerAuth(tokens.admin(ADMIN_ID)))
            .exchange()
            .expectStatus().isBadRequest();
    }

    @ParameterizedTest(name = "size={0}")
    @ValueSource(ints = {1, 100})
    @DisplayName("size가 경계(1 · 100)면 200")
    void sizeAtBoundaryIsOk(int size) {
        failDelivery(BUYER_ID);

        client.get().uri(RECOVERIES_PATH + "?size=" + size)
            .headers(headers -> headers.setBearerAuth(tokens.admin(ADMIN_ID)))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content.length()").isEqualTo(1)
            .jsonPath("$.size").isEqualTo(size);
    }

    @Test
    @DisplayName("큰 page: offset이 int 최댓값과 같거나(2147483647 · size 1) 넘어도(1073741824 · size 2) 빈 목록 · 실제 건수")
    void hugePages() {
        failDelivery(BUYER_ID);

        client.get().uri(RECOVERIES_PATH + "?page=2147483647&size=1")
            .headers(headers -> headers.setBearerAuth(tokens.admin(ADMIN_ID)))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content.length()").isEqualTo(0)
            .jsonPath("$.totalElements").isEqualTo(1);
        client.get().uri(RECOVERIES_PATH + "?page=1073741824&size=2")
            .headers(headers -> headers.setBearerAuth(tokens.admin(ADMIN_ID)))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content.length()").isEqualTo(0)
            .jsonPath("$.page").isEqualTo(1073741824)
            .jsonPath("$.totalElements").isEqualTo(1)
            .jsonPath("$.hasNext").isEqualTo(false);
    }

    @Test
    @DisplayName("재처리: 원인이 남아 있으면 200 · PENDING · 시도 2, 고친 뒤면 200 · RECOVERED, 다시 눌러도 200 · RECOVERED")
    void retry() {
        OrderDetail order = failDelivery(BUYER_ID);
        String recoveryId = recoveryIdOf(order);

        post(recoveryId, "retry").expectStatus().isOk()
            .expectBody()
            .jsonPath("$.status").isEqualTo("PENDING")
            .jsonPath("$.attemptCount").isEqualTo(2);
        fixSeller(order);
        post(recoveryId, "retry").expectStatus().isOk().expectBody().jsonPath("$.status").isEqualTo("RECOVERED");
        post(recoveryId, "retry").expectStatus().isOk().expectBody().jsonPath("$.status").isEqualTo("RECOVERED");

        assertThat(orderService.getOrder(BUYER_ID, order.orderId()).status()).isEqualTo("PAID");
    }

    @Test
    @DisplayName("재처리 불가 기록을 재처리하면 409, 취소로 종료하면 200 · CANCELLED")
    void notRetryable() {
        OrderDetail order = place(BUYER_ID);
        failWith(order, PAYMENT_ID, BUYER_ID + 1);
        String recoveryId = recoveryIdOf(order);

        post(recoveryId, "retry").expectStatus().isEqualTo(409)
            .expectBody()
            .jsonPath("$.title").isEqualTo("재처리할 수 없는 기록입니다")
            .jsonPath("$.detail").isEqualTo("결제 내용이 주문과 달라 재처리할 수 없습니다. 취소로 종료해 환불해 주세요.");
        post(recoveryId, "cancel").expectStatus().isOk().expectBody().jsonPath("$.status").isEqualTo("CANCELLED");

        assertThat(orderService.getOrder(BUYER_ID, order.orderId()).cancelReason()).isEqualTo("PAYMENT_FAILED");
    }

    @Test
    @DisplayName("취소로 종료: 200 · CANCELLED, 다시 눌러도 200, 그 뒤 재처리는 409(이미 종료)")
    void cancel() {
        OrderDetail order = failDelivery(BUYER_ID);
        String recoveryId = recoveryIdOf(order);

        post(recoveryId, "cancel").expectStatus().isOk().expectBody().jsonPath("$.status").isEqualTo("CANCELLED");
        post(recoveryId, "cancel").expectStatus().isOk().expectBody().jsonPath("$.status").isEqualTo("CANCELLED");
        post(recoveryId, "retry").expectStatus().isEqualTo(409)
            .expectBody()
            .jsonPath("$.title").isEqualTo("이미 종료된 기록입니다");
    }

    @Test
    @DisplayName("복구된 기록을 취소로 종료하면 409(이미 종료), 없는 기록은 재처리 · 취소 모두 404")
    void recoveredAndMissing() {
        OrderDetail order = failDelivery(BUYER_ID);
        String recoveryId = recoveryIdOf(order);
        fixSeller(order);
        post(recoveryId, "retry").expectStatus().isOk();

        post(recoveryId, "cancel").expectStatus().isEqualTo(409)
            .expectBody()
            .jsonPath("$.title").isEqualTo("이미 종료된 기록입니다")
            .jsonPath("$.detail").isEqualTo("이미 복구됐거나 취소로 종료된 주문입니다. 결제 반영 실패 목록을 새로고침해 주세요.");
        for (String action : List.of("retry", "cancel")) {
            post("999999", action).expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.title").isEqualTo("결제 반영 실패 기록을 찾을 수 없습니다");
        }
    }

    @Test
    @DisplayName("토큰이 없으면 401, 구매자 · 판매자 토큰이면 403(목록 · 재처리 · 취소)")
    void adminOnly() {
        client.get().uri(RECOVERIES_PATH).exchange().expectStatus().isUnauthorized();
        client.post().uri(RECOVERIES_PATH + "/1/retry").exchange().expectStatus().isUnauthorized();
        for (String token : List.of(tokens.buyer(BUYER_ID), tokens.seller(SELLER_ID))) {
            client.get().uri(RECOVERIES_PATH)
                .headers(headers -> headers.setBearerAuth(token))
                .exchange().expectStatus().isForbidden();
            client.post().uri(RECOVERIES_PATH + "/1/retry")
                .headers(headers -> headers.setBearerAuth(token))
                .exchange().expectStatus().isForbidden();
            client.post().uri(RECOVERIES_PATH + "/1/cancel")
                .headers(headers -> headers.setBearerAuth(token))
                .exchange().expectStatus().isForbidden();
        }
    }

    private RestTestClient.ResponseSpec post(String recoveryId, String action) {
        return client.post().uri(RECOVERIES_PATH + "/" + recoveryId + "/" + action)
            .headers(headers -> headers.setBearerAuth(tokens.admin(ADMIN_ID)))
            .exchange();
    }

    private OrderDetail place(Long buyerId) {
        cartService.addItem(buyerId, 1L, null, 1);
        cartService.addItem(buyerId, 2L, null, 1);
        return orderService.place(buyerId, new PlaceOrderCommand(
            List.of(new PlaceOrderCommand.Item(1L, null, 1, 25_000L), new PlaceOrderCommand.Item(2L, null, 1, 30_000L)),
            new PlaceOrderCommand.ShippingAddress("홍길동", "010-0000-0000", "12345", "서울시 테스트로 1")));
    }

    /**
     * 판매자 B 주문상품의 seller_id를 NULL로 바꾼 뒤 맞는 결제 완료를 보내 배송 생성 실패로 보관한다.
     */
    private OrderDetail failDelivery(Long buyerId) {
        OrderDetail order = place(buyerId);
        jdbcTemplate.update("update orders.order_item set seller_id = null where order_id = ? and seller_id = ?",
            order.orderId(), OTHER_SELLER_ID);
        failWith(order, PAYMENT_ID, buyerId);
        return order;
    }

    private void failWith(OrderDetail order, Long paymentId, Long memberId) {
        LocalDateTime now = LocalDateTime.now();
        assertThatThrownBy(() -> eventDelivery.invoke(new PaymentCompletedEvent(UUID.randomUUID(),
            now, new PaymentCompletedEvent.Data(paymentId, order.orderId(), memberId, order.totalAmount(),
            order.items().stream().map(item -> new PaymentCompletedEvent.Item(item.orderItemId(), item.amount()))
                .toList(), now)))).isInstanceOf(RuntimeException.class);
    }

    private void fixSeller(OrderDetail order) {
        jdbcTemplate.update("update orders.order_item set seller_id = ? where order_id = ? and seller_id is null",
            OTHER_SELLER_ID, order.orderId());
    }

    private String recoveryIdOf(OrderDetail order) {
        return String.valueOf(recoveryRepository.findByOrderId(order.orderId()).orElseThrow().getId());
    }
}
