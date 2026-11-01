package com.bbcc.kidly.boundedcontext.delivery.app;

import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.OCCURRED_AT;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.SELLER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.delivery.app.port.DeliveryRepository;
import com.bbcc.kidly.boundedcontext.delivery.domain.Delivery;
import com.bbcc.kidly.boundedcontext.order.ConcurrentCalls;
import com.bbcc.kidly.boundedcontext.order.MarkPaidCommands;
import com.bbcc.kidly.boundedcontext.order.OrderFlowEventRecorder;
import com.bbcc.kidly.boundedcontext.order.app.OrderErrorCode;
import com.bbcc.kidly.boundedcontext.order.app.order.OrderService;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.AutoConfirmTarget;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.PlaceOrderCommand;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderEventFailureRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.PaymentRecoveryRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.ProductReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.app.stuck.StuckOrderKind;
import com.bbcc.kidly.boundedcontext.order.app.stuck.StuckOrderService;
import com.bbcc.kidly.boundedcontext.order.domain.recovery.OrderEventFailure;
import com.bbcc.kidly.boundedcontext.order.domain.recovery.PaymentRecovery;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaProduct;
import com.bbcc.kidly.boundedcontext.support.IntegrationTest;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.shared.order.event.OrderCancelledEvent;
import com.bbcc.kidly.shared.order.event.OrderItemCancelledEvent;
import com.bbcc.kidly.shared.order.event.OrderItemPurchaseConfirmedEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 판매자 발송 전 부분 취소(#655, 실제 PostgreSQL): 같은 트랜잭션 · 발송과 동시 · 주문 안 후속(구매확정 · 자동 구매확정 · 멈춘 주문 ·
 * 구매확정 이벤트)에서 빠지는지. 주문은 판매자 2(옵션 1) · 판매자 3(옵션 2) 몫으로 결제완료다.
 * 테스트에 @Transactional을 붙이지 않는다(작업마다 자기 트랜잭션으로 커밋).
 */
@IntegrationTest
@Timeout(value = 120, unit = TimeUnit.SECONDS)
class DeliverySellerCancelIntegrationTest {

    private static final Long OTHER_SELLER_ID = 3L;
    private static final int REPEAT = 10;

    @Autowired
    private DeliveryService deliveryService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private StuckOrderService stuckOrderService;

    @Autowired
    private OrderFlowEventRecorder recorder;

    @Autowired
    private ProductReplicaRepository productReplicaRepository;

    @Autowired
    private DeliveryRepository deliveryRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private OrderEventFailureRepository failureRepository;

    @Autowired
    private PaymentRecoveryRepository recoveryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        recorder.clear();
        productReplicaRepository.save(ReplicaProduct.create(ReplicaFixture.onSaleProduct(25_000L, OCCURRED_AT)));
        productReplicaRepository.save(ReplicaProduct.create(new ReplicaProduct.Values(
            2L, 2L, OTHER_SELLER_ID, "상품 B", null, "120", 30_000L, 2L, "ON_SALE", null, false, OCCURRED_AT)));
    }

    @Test
    @DisplayName("취소를 감싼 트랜잭션이 롤백되면 배송 READY · 주문상품 ORDERED 그대로, 취소 기록 · 아웃박스 · 이력 · Spring 이벤트 없음")
    void rollsBackTogether() {
        Long orderId = placeAndPay(1L);
        Long deliveryId = deliveryIdOf(orderId, SELLER_ID);
        jdbcTemplate.update("delete from orders.order_outbox");
        recorder.clear();

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            deliveryService.cancel(SELLER_ID, deliveryId, "OUT_OF_STOCK", null);
            throw new IllegalStateException("업무 실패");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(jdbcTemplate.queryForObject("select status from orders.delivery where id = ?", String.class,
            deliveryId)).isEqualTo("READY");
        assertThat(jdbcTemplate.queryForList("select status from orders.order_item where order_id = ?", String.class,
            orderId)).containsOnly("ORDERED");
        assertThat(count("select count(*) from orders.order_item_cancellation")).isZero();
        assertThat(count("select count(*) from orders.order_outbox")).isZero();
        assertThat(count("select count(*) from orders.order_status_history where to_status = 'CANCELLED'")).isZero();
        assertThat(recorder.events(OrderItemCancelledEvent.class)).isEmpty();
    }

    @Test
    @DisplayName("같은 몫에 발송과 취소가 동시에 오면 한쪽만 성공: 취소가 이기면 CANCELLED · 이벤트 1, 발송이 이기면 SHIPPING · 이벤트 0")
    void cancelAndShipConcurrently() throws InterruptedException {
        for (int round = 1; round <= REPEAT; round++) {
            Long orderId = placeAndPay(1_000L + round);
            Long deliveryId = deliveryIdOf(orderId, SELLER_ID);
            recorder.clear();

            ConcurrentCalls.Outcome outcome = ConcurrentCalls.run(List.of(
                () -> deliveryService.ship(SELLER_ID, deliveryId, "CJ", "1111111111"),
                () -> deliveryService.cancel(SELLER_ID, deliveryId, "OUT_OF_STOCK", null)),
                DeliverySellerCancelIntegrationTest::lostByStateCheck);

            assertThat(outcome.unexpected()).as("%d회차 예외", round).isEmpty();
            assertThat(outcome.conflicts()).as("%d회차 진 쪽", round).isEqualTo(1);
            Delivery delivery = deliveryRepository.findByIdAndSellerId(deliveryId, SELLER_ID).orElseThrow();
            long cancelledItems = count("select count(*) from orders.order_item where order_id = " + orderId
                + " and status = 'CANCELLED'");
            long events = recorder.events(OrderItemCancelledEvent.class).stream()
                .filter(event -> event.data().orderId().equals(orderId))
                .count();
            if (delivery.getStatus() == Delivery.Status.CANCELLED) {
                assertThat(cancelledItems).as("%d회차 취소 이김", round).isEqualTo(1);
                assertThat(events).as("%d회차 취소 이김", round).isEqualTo(1);
                assertThat(delivery.getTrackingNumber()).isNull();
            } else {
                assertThat(delivery.getStatus()).as("%d회차 발송 이김", round).isEqualTo(Delivery.Status.SHIPPING);
                assertThat(cancelledItems).as("%d회차 발송 이김", round).isZero();
                assertThat(events).as("%d회차 발송 이김", round).isZero();
            }
        }
    }

    @Test
    @DisplayName("두 판매자가 남은 몫을 동시에 취소해도 둘 다 성공하고 주문은 CANCELLED 1회(주문 이력 1줄) · "
        + "order.cancelled 0 · order-item.cancelled 2")
    void twoSellersCancelLastSharesConcurrently() throws InterruptedException {
        for (int round = 1; round <= REPEAT; round++) {
            Long orderId = placeAndPay(2_000L + round);
            Long deliveryId = deliveryIdOf(orderId, SELLER_ID);
            Long otherDeliveryId = deliveryIdOf(orderId, OTHER_SELLER_ID);
            recorder.clear();

            ConcurrentCalls.Outcome outcome = ConcurrentCalls.run(List.of(
                () -> deliveryService.cancel(SELLER_ID, deliveryId, "OUT_OF_STOCK", null),
                () -> deliveryService.cancel(OTHER_SELLER_ID, otherDeliveryId, "OTHER", null)));

            assertThat(outcome.unexpected()).as("%d회차 예외", round).isEmpty();
            assertThat(outcome.conflicts()).as("%d회차 진 쪽 없음", round).isZero();
            assertThat(jdbcTemplate.queryForObject(
                "select status || ':' || cancel_reason from orders.orders where id = ?", String.class, orderId))
                .as("%d회차 주문", round).isEqualTo("CANCELLED:SELLER_CANCELLED");
            assertThat(count("select count(*) from orders.order_status_history where order_id = " + orderId
                + " and target_type = 'ORDER' and to_status = 'CANCELLED'")).as("%d회차 주문 이력", round).isEqualTo(1);
            assertThat(recorder.events(OrderCancelledEvent.class)).as("%d회차", round).isEmpty();
            assertThat(recorder.events(OrderItemCancelledEvent.class)).as("%d회차", round).hasSize(2);
        }
    }

    @Test
    @DisplayName("구매자 전체 취소와 판매자 취소가 동시에 와도 교착 · 500 없이 끝난다: 겹치면 한쪽만(진 쪽 낙관적 락 · 409), "
        + "판매자가 먼저 끝났으면 구매자 취소도 성공(일부 취소 뒤 전체 취소)")
    void buyerAndSellerCancelConcurrently() throws InterruptedException {
        for (int round = 1; round <= REPEAT; round++) {
            Long buyerId = 3_000L + round;
            Long orderId = placeAndPay(buyerId);
            Long deliveryId = deliveryIdOf(orderId, SELLER_ID);
            recorder.clear();

            ConcurrentCalls.Outcome outcome = ConcurrentCalls.run(List.of(
                () -> orderService.cancelByBuyer(buyerId, orderId),
                () -> deliveryService.cancel(SELLER_ID, deliveryId, "OUT_OF_STOCK", null)),
                DeliverySellerCancelIntegrationTest::lostToCancel);

            assertThat(outcome.unexpected()).as("%d회차 예외", round).isEmpty();
            long buyerCancelled = recorder.events(OrderCancelledEvent.class).size();
            long sellerCancelled = recorder.events(OrderItemCancelledEvent.class).size();
            assertThat(buyerCancelled).as("%d회차", round).isLessThanOrEqualTo(1);
            assertThat(sellerCancelled).as("%d회차", round).isLessThanOrEqualTo(1);
            assertThat(outcome.conflicts()).as("%d회차 진 쪽 = 2 − 성공", round)
                .isEqualTo(2 - (int) (buyerCancelled + sellerCancelled));
            assertThat(jdbcTemplate.queryForObject("select status from orders.orders where id = ?", String.class,
                orderId)).as("%d회차 주문", round).isEqualTo(buyerCancelled == 1 ? "CANCELLED" : "PAID");
            assertThat(jdbcTemplate.queryForObject("select status from orders.order_item where order_id = ?"
                    + " and option_id = 1", String.class, orderId)).as("%d회차 판매자 몫", round)
                .isEqualTo(sellerCancelled == 1 ? "CANCELLED" : "ORDERED");
        }
    }

    @Test
    @DisplayName("판매자 취소로 주문이 CANCELLED가 되면 그 주문의 재처리 대기 기록을 같은 트랜잭션에서 ORDER_CANCELLED로 닫는다")
    void lastShareClosesWaitingRecords() {
        Long buyerId = 1L;
        Long orderId = placeAndPay(buyerId);
        Long failureId = failureRepository.save(OrderEventFailure.open(OrderEventFailure.EventType.STOCK_DEDUCTED,
            orderId, List.of(), "DataIntegrityViolationException", LocalDateTime.now())).getId();
        Long recoveryId = recoveryRepository.save(PaymentRecovery.open(orderId, 500L, buyerId, 55_000L, List.of(),
            "DataIntegrityViolationException", true, LocalDateTime.now())).getId();

        deliveryService.cancel(SELLER_ID, deliveryIdOf(orderId, SELLER_ID), "OUT_OF_STOCK", null);

        assertThat(jdbcTemplate.queryForObject("select status from orders.order_event_failure where id = ?",
            String.class, failureId)).as("주문이 아직 PAID면 닫지 않는다").isEqualTo("PENDING");

        deliveryService.cancel(OTHER_SELLER_ID, deliveryIdOf(orderId, OTHER_SELLER_ID), "OTHER", null);

        assertThat(jdbcTemplate.queryForMap("select status, failure_code from orders.order_event_failure where id = ?",
            failureId)).containsEntry("status", "CANCELLED").containsEntry("failure_code", "ORDER_CANCELLED");
        assertThat(jdbcTemplate.queryForMap("select status, failure_code from orders.payment_recovery where id = ?",
            recoveryId)).containsEntry("status", "CANCELLED").containsEntry("failure_code", "ORDER_CANCELLED");
        assertThat(jdbcTemplate.queryForList("select actor_type || ' ' || source from orders.order_status_history"
            + " where order_id = ? and target_type = 'PAYMENT_RECOVERY'", String.class, orderId))
            .containsExactly("SELLER POST /api/v1/seller/orders/{sellerOrderId}/cancel");
    }

    @Test
    @DisplayName("취소된 주문상품은 직접 구매확정(409) · 자동 구매확정 대상 · 멈춘 주문(자동 구매확정 누락 · 발송 지연) · 구매확정 이벤트에서 빠진다")
    void cancelledItemsAreExcludedFromFollowUps() {
        Long buyerId = 1L;
        Long orderId = placeAndPay(buyerId);
        Long cancelledItemId = orderItemIdOf(orderId, 1L);
        Long otherItemId = orderItemIdOf(orderId, 2L);
        deliveryService.cancel(SELLER_ID, deliveryIdOf(orderId, SELLER_ID), "OUT_OF_STOCK", null);
        Long otherDeliveryId = deliveryIdOf(orderId, OTHER_SELLER_ID);
        deliveryService.ship(OTHER_SELLER_ID, otherDeliveryId, "CJ", "1234567890");
        deliveryService.complete(OTHER_SELLER_ID, otherDeliveryId);
        LocalDateTime longAgo = LocalDateTime.now().minusDays(60);
        // 오래전에 만들고 배송 완료된 것처럼 시각을 당긴다(취소된 배송 · 주문상품도 함께)
        jdbcTemplate.update("update orders.delivery set created_at = ? where order_id = ?", longAgo, orderId);
        jdbcTemplate.update("update orders.order_item set delivered_at = ? where id = ?", longAgo, otherItemId);
        LocalDateTime now = LocalDateTime.now();

        assertThatThrownBy(() -> orderService.confirmByBuyer(buyerId, orderId, cancelledItemId))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").extracting("title").isEqualTo("취소된 주문상품입니다");
        assertThat(orderService.findAutoConfirmTargets(now, 0L, 100)).extracting(AutoConfirmTarget::orderItemId)
            .containsExactly(otherItemId);
        assertThat(stuckOrderService.getSummary(now))
            .containsEntry(StuckOrderKind.AUTO_CONFIRM_MISSED, 1L)
            .containsEntry(StuckOrderKind.SHIPPING_DELAYED, 0L);

        orderService.confirmAutomatically(orderId, cancelledItemId);
        orderService.confirmAutomatically(orderId, otherItemId);

        assertThat(recorder.events(OrderItemPurchaseConfirmedEvent.class))
            .extracting(event -> event.data().orderItemId()).containsExactly(otherItemId);
        assertThat(jdbcTemplate.queryForObject("select status from orders.order_item where id = ?", String.class,
            cancelledItemId)).isEqualTo("CANCELLED");
    }

    // 진 쪽이 이긴 쪽 커밋 뒤에 시작하면 낙관적 락이 아니라 상태 검사로 끝난다: 발송 뒤 취소는 ALREADY_SHIPPED,
    // 취소 뒤 발송은 DELIVERY_CANCELLED
    private static boolean lostByStateCheck(Throwable error) {
        return error instanceof BusinessException business
            && (business.getErrorCode() == DeliveryErrorCode.ALREADY_SHIPPED
            || business.getErrorCode() == DeliveryErrorCode.DELIVERY_CANCELLED);
    }

    // 구매자 취소와 판매자 취소가 겹칠 때 진 쪽: 낙관적 락 말고도 이긴 쪽 커밋 뒤에 상태를 보고 막히면 409다
    // (판매자 뒤 구매자: 상태 검사는 통과하고 배송 버전으로 낙관적 락. 구매자 뒤 판매자: 이미 취소 409)
    private static boolean lostToCancel(Throwable error) {
        return error instanceof BusinessException business
            && (business.getErrorCode() == DeliveryErrorCode.ALREADY_CANCELLED
            || business.getErrorCode() == OrderErrorCode.CANCEL_NOT_ALLOWED_SHIPPED);
    }

    private Long placeAndPay(Long buyerId) {
        Long orderId = orderService.place(buyerId, new PlaceOrderCommand(
            List.of(new PlaceOrderCommand.Item(1L, null, 1, 25_000L), new PlaceOrderCommand.Item(2L, null, 1, 30_000L)),
            new PlaceOrderCommand.ShippingAddress("홍길동", "010-0000-0000", "12345", "서울시 테스트로 1"))).orderId();
        orderService.markPaid(MarkPaidCommands.matching(orderService.getOrder(buyerId, orderId), buyerId, 500L));
        deliveryService.createFor(orderId);
        return orderId;
    }

    private Long deliveryIdOf(Long orderId, Long sellerId) {
        return jdbcTemplate.queryForObject("select id from orders.delivery where order_id = ? and seller_id = ?",
            Long.class, orderId, sellerId);
    }

    private Long orderItemIdOf(Long orderId, Long optionId) {
        return jdbcTemplate.queryForObject("select id from orders.order_item where order_id = ? and option_id = ?",
            Long.class, orderId, optionId);
    }

    private long count(String sql) {
        return jdbcTemplate.queryForObject(sql, Long.class);
    }
}
