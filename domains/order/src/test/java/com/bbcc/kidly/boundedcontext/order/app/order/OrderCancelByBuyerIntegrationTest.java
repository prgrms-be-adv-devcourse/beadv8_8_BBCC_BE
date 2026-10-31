package com.bbcc.kidly.boundedcontext.order.app.order;

import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.OCCURRED_AT;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.SELLER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.order.ConcurrentCalls;
import com.bbcc.kidly.boundedcontext.order.MarkPaidCommands;
import com.bbcc.kidly.boundedcontext.order.OrderEventDelivery;
import com.bbcc.kidly.boundedcontext.order.OrderFlowEventRecorder;
import com.bbcc.kidly.boundedcontext.order.app.OrderErrorCode;
import com.bbcc.kidly.boundedcontext.order.app.cart.CartService;
import com.bbcc.kidly.boundedcontext.order.app.delivery.DeliveryService;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.OrderDetail;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.PlaceOrderCommand;
import com.bbcc.kidly.boundedcontext.order.app.port.CartItemRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.DeliveryRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.PaymentRecoveryRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.ProductReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.domain.delivery.Delivery;
import com.bbcc.kidly.boundedcontext.order.domain.order.Order;
import com.bbcc.kidly.boundedcontext.order.domain.recovery.PaymentRecovery;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaProduct;
import com.bbcc.kidly.boundedcontext.order.in.web.PaymentRecoveryRetry;
import com.bbcc.kidly.boundedcontext.order.support.IntegrationTest;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.shared.order.event.OrderCancelledEvent;
import com.bbcc.kidly.shared.payment.event.PaymentCompletedEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 구매자 전체 취소(#354, 실제 PostgreSQL): 동시 요청 · V8 스키마. 테스트에 @Transactional을 붙이지 않는다.
 * 동시 요청은 반복마다 구매자를 바꾸고, 결과(주문 · 배송 상태 · order.cancelled 수)로 불변식을 본다(장벽 · sleep 없음).
 * 취소 · 발송 · 만료 · 결제 완료의 진 쪽은 낙관적 락(공통 409) 또는 상태 검사의 409로 끝나는 것이 정상이다.
 */
@IntegrationTest
@Timeout(value = 120, unit = TimeUnit.SECONDS)
class OrderCancelByBuyerIntegrationTest {

    private static final Long OTHER_SELLER_ID = 3L;
    private static final int REPEAT = 10;
    private static final Long ADMIN_ID = 9_000L;

    @Autowired
    private OrderEventDelivery eventDelivery;

    @Autowired
    private OrderService orderService;

    @Autowired
    private DeliveryService deliveryService;

    @Autowired
    private CartService cartService;

    @Autowired
    private ProductReplicaRepository productReplicaRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private DeliveryRepository deliveryRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private OrderFlowEventRecorder recorder;

    @Autowired
    private PaymentRecoveryRepository recoveryRepository;

    @Autowired
    private PaymentRecoveryService recoveryService;

    @Autowired
    private PaymentRecoveryRetry recoveryRetry;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        recorder.clear();
        productReplicaRepository.save(ReplicaProduct.create(ReplicaFixture.onSaleProduct(25_000L, OCCURRED_AT)));
        productReplicaRepository.save(ReplicaProduct.create(new ReplicaProduct.Values(
            2L, 2L, OTHER_SELLER_ID, "상품 B", null, "120", 30_000L, 2L, "ON_SALE", null, false, OCCURRED_AT)));
    }

    @Test
    @DisplayName("판매자 1명 결제완료 주문에 발송 · 취소가 동시에 오면 한쪽만: 발송(PAID · SHIPPING · 이벤트 0) 또는 취소(CANCELLED · 이벤트 1)")
    void shipVersusCancel() throws InterruptedException {
        for (int round = 1; round <= REPEAT; round++) {
            Long buyerId = 1_000L + round;
            Long orderId = placeAndPay(buyerId, false);
            Long deliveryId = deliveryOf(orderId, SELLER_ID).getId();

            ConcurrentCalls.Outcome outcome = ConcurrentCalls.run(List.of(
                () -> deliveryService.ship(SELLER_ID, deliveryId, "CJ", "1234567890"),
                () -> orderService.cancelByBuyer(buyerId, orderId)),
                OrderCancelByBuyerIntegrationTest::isRejectedByState);

            assertThat(outcome.unexpected()).as("%d회차 예외", round).isEmpty();
            assertThat(outcome.conflicts()).as("%d회차 진 쪽(낙관적 락 · 상태 409)", round).isEqualTo(1);
            Order order = orderRepository.findById(orderId).orElseThrow();
            Delivery delivery = deliveryOf(orderId, SELLER_ID);
            if (delivery.getStatus() == Delivery.Status.SHIPPING) {
                assertThat(order.getStatus()).as("%d회차", round).isEqualTo(Order.Status.PAID);
                assertThat(cancelledEvents(orderId)).as("%d회차", round).isZero();
            } else {
                assertThat(delivery.getStatus()).as("%d회차", round).isEqualTo(Delivery.Status.CANCELLED);
                assertThat(order.getStatus()).as("%d회차", round).isEqualTo(Order.Status.CANCELLED);
                assertThat(order.getCancelReason()).isEqualTo(Order.CancelReason.BUYER_CANCELLED);
                assertThat(cancelledEvents(orderId)).as("%d회차", round).isEqualTo(1);
            }
        }
    }

    @Test
    @DisplayName("판매자 2명 중 A 발송과 취소가 동시에 와도 부분 반영 없음: A 발송 · B 발송 전 · PAID 또는 둘 다 CANCELLED")
    void shipOneOfTwoVersusCancel() throws InterruptedException {
        for (int round = 1; round <= REPEAT; round++) {
            Long buyerId = 2_000L + round;
            Long orderId = placeAndPay(buyerId, true);
            Long deliveryA = deliveryOf(orderId, SELLER_ID).getId();

            ConcurrentCalls.Outcome outcome = ConcurrentCalls.run(List.of(
                () -> deliveryService.ship(SELLER_ID, deliveryA, "CJ", "1234567890"),
                () -> orderService.cancelByBuyer(buyerId, orderId)),
                OrderCancelByBuyerIntegrationTest::isRejectedByState);

            assertThat(outcome.unexpected()).as("%d회차 예외", round).isEmpty();
            assertThat(outcome.conflicts()).as("%d회차 진 쪽(낙관적 락 · 상태 409)", round).isEqualTo(1);
            Order order = orderRepository.findById(orderId).orElseThrow();
            List<Delivery.Status> statuses = deliveryRepository.findAllByOrderIdOrderByIdAsc(orderId).stream()
                .map(Delivery::getStatus).toList();
            if (order.getStatus() == Order.Status.PAID) {
                assertThat(statuses).as("%d회차", round).containsExactly(Delivery.Status.SHIPPING, Delivery.Status.READY);
                assertThat(cancelledEvents(orderId)).as("%d회차", round).isZero();
            } else {
                assertThat(statuses).as("%d회차", round)
                    .containsExactly(Delivery.Status.CANCELLED, Delivery.Status.CANCELLED);
                assertThat(cancelledEvents(orderId)).as("%d회차", round).isEqualTo(1);
            }
        }
    }

    @Test
    @DisplayName("스레드 없이: 읽어 둔 배송을 다른 트랜잭션이 먼저 발송해 커밋하면, 읽어 둔 쪽의 취소는 커밋 때 낙관적 락으로 실패한다")
    void staleDeliveryCancelFailsByVersion() {
        Long orderId = placeAndPay(2_500L, false);
        Long deliveryId = deliveryOf(orderId, SELLER_ID).getId();
        TransactionTemplate outer = new TransactionTemplate(transactionManager);
        TransactionTemplate inner = new TransactionTemplate(transactionManager);
        inner.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        assertThatThrownBy(() -> outer.executeWithoutResult(status -> {
            Delivery stale = deliveryOf(orderId, SELLER_ID);
            inner.executeWithoutResult(newStatus -> deliveryService.ship(SELLER_ID, deliveryId, "CJ", "1234567890"));
            stale.cancel();
        })).isInstanceOf(ObjectOptimisticLockingFailureException.class);

        assertThat(deliveryOf(orderId, SELLER_ID).getStatus()).isEqualTo(Delivery.Status.SHIPPING);
    }

    @Test
    @DisplayName("결제대기 주문에 구매자 취소와 결제 마감 만료가 동시에 와도 order.cancelled는 1건")
    void cancelVersusExpiry() throws InterruptedException {
        for (int round = 1; round <= REPEAT; round++) {
            Long buyerId = 3_000L + round;
            Long orderId = place(buyerId, false);
            jdbcTemplate.update("update orders.orders set payment_due_at = ? where id = ?",
                LocalDateTime.now().minusMinutes(1), orderId);

            ConcurrentCalls.Outcome outcome = ConcurrentCalls.run(List.of(
                () -> orderService.cancelByBuyer(buyerId, orderId),
                () -> orderService.cancelForPaymentExpiry(orderId)));

            assertThat(outcome.unexpected()).as("%d회차 예외", round).isEmpty();
            assertThat(outcome.conflicts()).as("%d회차 진 쪽(낙관적 락)", round).isLessThanOrEqualTo(1);
            assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(Order.Status.CANCELLED);
            assertThat(cancelledEvents(orderId)).as("%d회차 order.cancelled", round).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("결제대기 주문에 구매자 취소와 결제 완료가 동시에 오면: 취소가 이기면 CANCELLED · 배송 0 · 장바구니 그대로, 결제가 이기면 PAID 뒤 취소 가능")
    void cancelVersusPaymentCompleted() throws InterruptedException {
        for (int round = 1; round <= REPEAT; round++) {
            Long buyerId = 4_000L + round;
            Long orderId = place(buyerId, true);
            PaymentCompletedEvent completed = completedFor(orderService.getOrder(buyerId, orderId), buyerId);

            ConcurrentCalls.Outcome outcome = ConcurrentCalls.run(List.of(
                () -> orderService.cancelByBuyer(buyerId, orderId),
                () -> eventDelivery.invoke(completed)),
                OrderCancelByBuyerIntegrationTest::isRejectedByState);

            assertThat(outcome.unexpected()).as("%d회차 예외", round).isEmpty();
            Order order = orderRepository.findById(orderId).orElseThrow();
            List<Delivery> deliveries = deliveryRepository.findAllByOrderIdOrderByIdAsc(orderId);
            if (order.getStatus() == Order.Status.PAID) {
                assertThat(deliveries).extracting(Delivery::getStatus).containsOnly(Delivery.Status.READY);
                assertThat(cancelledEvents(orderId)).isZero();
                assertThat(orderService.cancelByBuyer(buyerId, orderId).status()).isEqualTo("CANCELLED");
                assertThat(cancelledEvents(orderId)).isEqualTo(1);
            } else if (order.getPaidAt() == null) {
                assertThat(deliveries).as("%d회차 결제 전 취소", round).isEmpty();
                assertThat(cartItemRepository.findAllByMemberIdOrderByIdDesc(buyerId)).hasSize(1);
                assertThat(cancelledEvents(orderId)).isEqualTo(1);
            } else {
                // 결제가 먼저 커밋되고 취소가 그 뒤에 결제완료 주문을 취소한 순서
                assertThat(deliveries).extracting(Delivery::getStatus).containsOnly(Delivery.Status.CANCELLED);
                assertThat(cancelledEvents(orderId)).isEqualTo(1);
            }
            // 진 쪽 결제 완료는 낙관적 락(동시 중복에서 진 쪽) 또는 취소된 주문이라 반영 안 함이어서 실패 보관이 없다(#353)
            assertThat(recoveryCount(orderId)).as("%d회차 결제 반영 실패 기록", round).isZero();
        }
    }

    @Test
    @DisplayName("결제 반영 실패 기록이 재처리 대기인 주문을 구매자가 취소하면 200, 기록은 그때 닫히고(ORDER_CANCELLED, #598) "
        + "그 뒤 관리자 재처리는 409(이벤트 1 그대로 — 결제 쪽 환불)")
    void buyerCancelThenAdminRetry() {
        Long buyerId = 8_000L;
        Long orderId = place(buyerId, true);
        Long recoveryId = pendingRecovery(orderId, buyerId);

        assertThat(orderService.cancelByBuyer(buyerId, orderId).status()).isEqualTo("CANCELLED");
        assertThat(cancelledEvents(orderId)).isEqualTo(1);

        assertThat(recoveryRepository.findById(recoveryId).orElseThrow()).satisfies(recovery -> {
            assertThat(recovery.getStatus()).isEqualTo(PaymentRecovery.Status.CANCELLED);
            assertThat(recovery.getFailureCode()).isEqualTo("ORDER_CANCELLED");
        });
        assertThatThrownBy(() -> recoveryRetry.retry(recoveryId, ADMIN_ID))
            .isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(OrderErrorCode.RECOVERY_CLOSED));

        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(Order.Status.CANCELLED);
        assertThat(order.getCancelReason()).isEqualTo(Order.CancelReason.BUYER_CANCELLED);
        assertThat(deliveryRepository.findAllByOrderIdOrderByIdAsc(orderId)).isEmpty();
        assertThat(cancelledEvents(orderId)).isEqualTo(1);
    }

    @Test
    @DisplayName("결제대기 주문에 구매자 취소와 관리자 기록 취소가 동시에 와도 order.cancelled는 1건, 진 쪽은 낙관적 락 · RECOVERY_CLOSED뿐")
    void buyerCancelVersusAdminRecoveryCancel() throws InterruptedException {
        for (int round = 1; round <= REPEAT; round++) {
            Long buyerId = 8_100L + round;
            Long orderId = place(buyerId, true);
            Long recoveryId = pendingRecovery(orderId, buyerId);

            ConcurrentCalls.Outcome outcome = ConcurrentCalls.run(List.of(
                () -> orderService.cancelByBuyer(buyerId, orderId),
                () -> recoveryService.cancel(recoveryId, ADMIN_ID)),
                error -> error instanceof BusinessException business
                    && business.getErrorCode() == OrderErrorCode.RECOVERY_CLOSED);

            assertThat(outcome.unexpected()).as("%d회차 예외", round).isEmpty();
            assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(Order.Status.CANCELLED);
            assertThat(cancelledEvents(orderId)).as("%d회차 order.cancelled", round).isEqualTo(1);
        }
    }

    /**
     * 결제대기 주문에 재처리 대기(PENDING) 결제 반영 실패 기록을 만든다(#353 리스너 실패 보관과 같은 모양).
     */
    private Long pendingRecovery(Long orderId, Long buyerId) {
        OrderDetail order = orderService.getOrder(buyerId, orderId);
        return recoveryRepository.save(PaymentRecovery.open(orderId, 500L, buyerId, order.totalAmount(),
            order.items().stream()
                .map(item -> new PaymentRecovery.PaidItem(item.orderItemId(), item.amount())).toList(),
            "DataIntegrityViolationException", true, LocalDateTime.now())).getId();
    }

    private long historyCount(Long orderId, String targetType, String toStatus) {
        return jdbcTemplate.queryForObject("select count(*) from orders.order_status_history "
            + "where order_id = ? and target_type = ? and to_status = ?", Long.class, orderId, targetType, toStatus);
    }

    private long recoveryCount(Long orderId) {
        return jdbcTemplate.queryForObject("select count(*) from orders.payment_recovery where order_id = ?",
            Long.class, orderId);
    }

    @Test
    @DisplayName("같은 주문에 구매자 취소 두 번이 동시에 와도 order.cancelled는 1건")
    void cancelTwiceConcurrently() throws InterruptedException {
        for (int round = 1; round <= REPEAT; round++) {
            Long buyerId = 5_000L + round;
            Long orderId = placeAndPay(buyerId, true);

            ConcurrentCalls.Outcome outcome = ConcurrentCalls.run(List.of(
                () -> orderService.cancelByBuyer(buyerId, orderId),
                () -> orderService.cancelByBuyer(buyerId, orderId)));

            assertThat(outcome.unexpected()).as("%d회차 예외", round).isEmpty();
            assertThat(outcome.conflicts()).as("%d회차 진 쪽(낙관적 락)", round).isLessThanOrEqualTo(1);
            assertThat(cancelledEvents(orderId)).as("%d회차 order.cancelled", round).isEqualTo(1);
            assertThat(deliveryRepository.findAllByOrderIdOrderByIdAsc(orderId)).extracting(Delivery::getStatus)
                .containsOnly(Delivery.Status.CANCELLED);
            assertThat(historyCount(orderId, "ORDER", "CANCELLED")).as("%d회차 주문 취소 이력", round).isEqualTo(1);
            assertThat(historyCount(orderId, "DELIVERY", "CANCELLED")).as("%d회차 배송 취소 이력", round).isEqualTo(2);
        }
    }

    @Test
    @DisplayName("V8: 버전 칼럼 없이 들어간 기존 배송 행도 0부터 시작해 발송된다")
    void existingDeliveryRowShips() {
        Long orderId = place(6_000L, false);
        jdbcTemplate.update("insert into orders.delivery (order_id, seller_id, status, created_at, updated_at) "
            + "values (?, ?, 'READY', now(), now())", orderId, SELLER_ID);
        Long deliveryId = deliveryOf(orderId, SELLER_ID).getId();
        assertThat(jdbcTemplate.queryForObject("select version from orders.delivery where id = ?", Long.class,
            deliveryId)).isZero();

        deliveryService.ship(SELLER_ID, deliveryId, "CJ", "1234567890");

        assertThat(deliveryOf(orderId, SELLER_ID).getStatus()).isEqualTo(Delivery.Status.SHIPPING);
        assertThat(jdbcTemplate.queryForObject("select version from orders.delivery where id = ?", Long.class,
            deliveryId)).isEqualTo(1L);
    }

    @Test
    @DisplayName("V8: 새 값(배송 CANCELLED · 취소 사유 BUYER_CANCELLED)은 저장되고 모르는 값은 DB가 막는다")
    void checkConstraints() {
        Long orderId = placeAndPay(7_000L, false);
        Long deliveryId = deliveryOf(orderId, SELLER_ID).getId();

        jdbcTemplate.update("update orders.delivery set status = 'CANCELLED' where id = ?", deliveryId);
        jdbcTemplate.update("update orders.orders set status = 'CANCELLED', cancel_reason = 'BUYER_CANCELLED' "
            + "where id = ?", orderId);

        assertThatThrownBy(() -> jdbcTemplate.update("update orders.delivery set status = 'LOST' where id = ?",
            deliveryId)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("update orders.orders set cancel_reason = 'SELLER_CANCELLED' "
            + "where id = ?", orderId)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(deliveryOf(orderId, SELLER_ID).getStatus()).isEqualTo(Delivery.Status.CANCELLED);
        assertThat(orderRepository.findById(orderId).orElseThrow().getCancelReason())
            .isEqualTo(Order.CancelReason.BUYER_CANCELLED);
    }

    /**
     * 진 쪽이 상대 커밋 뒤에 시작해 상태 검사로 막힌 경우(409): 발송된 주문 취소 · 취소된 배송 발송.
     */
    private static boolean isRejectedByState(Throwable error) {
        return error instanceof BusinessException business
            && (business.getErrorCode() == OrderErrorCode.CANCEL_NOT_ALLOWED_SHIPPED
                || business.getErrorCode() == OrderErrorCode.DELIVERY_CANCELLED);
    }

    /**
     * @param twoSellers true면 판매자 2명(옵션 1 · 2), false면 판매자 1명(옵션 1). 장바구니에 담고 주문한다
     */
    private Long place(Long buyerId, boolean twoSellers) {
        cartService.addItem(buyerId, 1L, null, 1);
        List<PlaceOrderCommand.Item> items = twoSellers
            ? List.of(new PlaceOrderCommand.Item(1L, null, 1, 25_000L),
                new PlaceOrderCommand.Item(2L, null, 1, 30_000L))
            : List.of(new PlaceOrderCommand.Item(1L, null, 1, 25_000L));
        return orderService.place(buyerId, new PlaceOrderCommand(items,
            new PlaceOrderCommand.ShippingAddress("홍길동", "010-0000-0000", "12345", "서울시 테스트로 1"))).orderId();
    }

    private Long placeAndPay(Long buyerId, boolean twoSellers) {
        Long orderId = place(buyerId, twoSellers);
        orderService.markPaid(MarkPaidCommands.matching(orderService.getOrder(buyerId, orderId), buyerId, 500L));
        deliveryService.createFor(orderId);
        return orderId;
    }

    private Delivery deliveryOf(Long orderId, Long sellerId) {
        return deliveryRepository.findAllByOrderIdOrderByIdAsc(orderId).stream()
            .filter(delivery -> delivery.getSellerId().equals(sellerId))
            .findFirst()
            .orElseThrow();
    }

    private long cancelledEvents(Long orderId) {
        return recorder.events(OrderCancelledEvent.class).stream()
            .filter(event -> event.data().orderId().equals(orderId))
            .count();
    }

    private static PaymentCompletedEvent completedFor(OrderDetail order, Long buyerId) {
        LocalDateTime now = LocalDateTime.now();
        return new PaymentCompletedEvent(UUID.randomUUID(), now, new PaymentCompletedEvent.Data(
            500L, order.orderId(), buyerId, order.totalAmount(),
            order.items().stream().map(item -> new PaymentCompletedEvent.Item(item.orderItemId(), item.amount()))
                .toList(), now));
    }
}
