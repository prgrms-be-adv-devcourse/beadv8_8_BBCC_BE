package com.bbcc.kidly.boundedcontext.order.app.order;

import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.OCCURRED_AT;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.SELLER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.cart.app.CartService;
import com.bbcc.kidly.boundedcontext.delivery.app.DeliveryService;
import com.bbcc.kidly.boundedcontext.delivery.app.port.DeliveryRepository;
import com.bbcc.kidly.boundedcontext.delivery.domain.Delivery;
import com.bbcc.kidly.boundedcontext.order.MarkPaidCommands;
import com.bbcc.kidly.boundedcontext.order.OrderDeliveredAtSetter;
import com.bbcc.kidly.boundedcontext.order.OrderEventDelivery;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.OrderDetail;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.PlaceOrderCommand;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.StockDeductionFailedCommand;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderStatusHistoryRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.PaymentRecoveryRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.ProductReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.domain.history.OrderStatusHistory;
import com.bbcc.kidly.boundedcontext.order.domain.order.PaymentMismatchException;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaProduct;
import com.bbcc.kidly.boundedcontext.order.in.web.admin.PaymentRecoveryRetry;
import com.bbcc.kidly.boundedcontext.support.IntegrationTest;
import com.bbcc.kidly.global.error.BusinessException;
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

/**
 * 주문 상태 변경 이력(#442, 실제 PostgreSQL): 상태가 이번에 바뀐 곳에서만 한 줄씩, 같은 요청 · 이벤트가 다시 오면 0줄.
 * 한 줄은 "대상 이전>다음 사유 주체 주체ID 출처"로 비교한다. 테스트에 @Transactional을 붙이지 않는다.
 * 결제 반영 실패는 PaymentRecoveryIntegrationTest와 같이 주문상품 seller_id를 NULL로 바꿔 배송 생성을 실패시킨다.
 */
@IntegrationTest
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class OrderStatusHistoryIntegrationTest {

    private static final Long BUYER_ID = 7_100L;
    private static final Long OTHER_SELLER_ID = 3L;
    private static final Long ADMIN_ID = 9_000L;
    private static final Long PAYMENT_ID = 500L;

    @Autowired
    private OrderEventDelivery eventDelivery;

    @Autowired
    private OrderService orderService;

    @Autowired
    private DeliveryService deliveryService;

    @Autowired
    private CartService cartService;

    @Autowired
    private PaymentRecoveryService recoveryService;

    @Autowired
    private PaymentRecoveryRetry recoveryRetry;

    @Autowired
    private ProductReplicaRepository productReplicaRepository;

    @Autowired
    private DeliveryRepository deliveryRepository;

    @Autowired
    private PaymentRecoveryRepository recoveryRepository;

    @Autowired
    private OrderStatusHistoryRepository historyRepository;

    @Autowired
    private OrderDeliveredAtSetter deliveredAtSetter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        productReplicaRepository.save(ReplicaProduct.create(ReplicaFixture.onSaleProduct(25_000L, OCCURRED_AT)));
        productReplicaRepository.save(ReplicaProduct.create(new ReplicaProduct.Values(
            2L, 2L, OTHER_SELLER_ID, "상품 B", null, "120", 30_000L, 2L, "ON_SALE", null, false, OCCURRED_AT)));
    }

    @Test
    @DisplayName("주문하기: 주문 null>PENDING_PAYMENT 1줄(구매자 · 주문 경로), 같은 요청 키로 다시 주문하면 0줄")
    void place() {
        PlaceOrderCommand command = command(false);
        String hash = OrderRequestFingerprint.of(command);
        Long orderId = orderService.placeIdempotently(BUYER_ID, command, "key-1", hash).detail().orderId();

        orderService.placeIdempotently(BUYER_ID, command, "key-1", hash);

        assertThat(rows(orderId)).containsExactly(
            "ORDER#" + orderId + " null>PENDING_PAYMENT null BUYER " + BUYER_ID + " POST /api/v1/orders");
    }

    @Test
    @DisplayName("결제 완료 이벤트: 주문 >PAID 1줄 · 판매자별 배송 null>READY 1줄씩(이벤트), 같은 결제 완료가 다시 오면 0줄")
    void paymentCompleted() {
        OrderDetail order = place(true);

        eventDelivery.invoke(completed(order));
        eventDelivery.invoke(completed(order));

        List<Delivery> deliveries = deliveryRepository.findAllByOrderIdOrderByIdAsc(order.orderId());
        assertThat(rows(order.orderId())).containsExactly(
            "ORDER#" + order.orderId() + " null>PENDING_PAYMENT null BUYER " + BUYER_ID + " POST /api/v1/orders",
            "ORDER#" + order.orderId() + " PENDING_PAYMENT>PAID null EVENT null payment.completed",
            "DELIVERY#" + deliveries.get(0).getId() + " null>READY null EVENT null payment.completed",
            "DELIVERY#" + deliveries.get(1).getId() + " null>READY null EVENT null payment.completed");
    }

    @Test
    @DisplayName("발송 · 배송 완료: 배송 READY>SHIPPING · SHIPPING>DELIVERED 1줄씩(판매자), 배송 완료를 다시 눌러도 0줄")
    void shipAndDeliver() {
        Long orderId = placeAndPay(false);
        Long deliveryId = deliveryRepository.findAllByOrderIdOrderByIdAsc(orderId).getFirst().getId();

        deliveryService.ship(SELLER_ID, deliveryId, "CJ", "1234567890");
        deliveryService.complete(SELLER_ID, deliveryId);
        deliveryService.complete(SELLER_ID, deliveryId);

        assertThat(rows(orderId)).containsSubsequence(
            "DELIVERY#" + deliveryId + " READY>SHIPPING null SELLER " + SELLER_ID
                + " POST /api/v1/seller/orders/{sellerOrderId}/ship",
            "DELIVERY#" + deliveryId + " SHIPPING>DELIVERED null SELLER " + SELLER_ID
                + " POST /api/v1/seller/orders/{sellerOrderId}/deliver");
        assertThat(rows(orderId)).filteredOn(row -> row.startsWith("DELIVERY#")).hasSize(3);
    }

    @Test
    @DisplayName("구매확정: 직접(구매자 · DIRECT) · 자동(시스템 · AUTO) 주문상품 ORDERED>CONFIRMED 1줄씩, 다시 확정해도 0줄")
    void confirm() {
        Long orderId = placeAndPay(true);
        List<OrderDetail.Item> items = orderService.getOrder(BUYER_ID, orderId).items();
        Long direct = items.get(0).orderItemId();
        Long auto = items.get(1).orderItemId();
        deliveredAtSetter.recordDelivered(orderId, SELLER_ID, LocalDateTime.now().minusDays(1));
        deliveredAtSetter.recordDelivered(orderId, OTHER_SELLER_ID, LocalDateTime.now().minusDays(30));

        orderService.confirmByBuyer(BUYER_ID, orderId, direct);
        orderService.confirmByBuyer(BUYER_ID, orderId, direct);
        orderService.confirmAutomatically(orderId, auto);
        orderService.confirmAutomatically(orderId, auto);

        assertThat(rows(orderId)).filteredOn(row -> row.startsWith("ORDER_ITEM#")).containsExactly(
            "ORDER_ITEM#" + direct + " ORDERED>CONFIRMED DIRECT BUYER " + BUYER_ID
                + " POST /api/v1/orders/{orderId}/items/{orderItemId}/confirm",
            "ORDER_ITEM#" + auto + " ORDERED>CONFIRMED AUTO SYSTEM null scheduler:auto-confirm");
    }

    @Test
    @DisplayName("구매자 취소(결제완료): 주문 PAID>CANCELLED · 배송 READY>CANCELLED 1줄씩(BUYER_CANCELLED), 다시 취소해도 0줄")
    void cancelByBuyer() {
        Long orderId = placeAndPay(true);
        List<Delivery> deliveries = deliveryRepository.findAllByOrderIdOrderByIdAsc(orderId);
        int before = rows(orderId).size();

        orderService.cancelByBuyer(BUYER_ID, orderId);
        orderService.cancelByBuyer(BUYER_ID, orderId);

        String source = " BUYER " + BUYER_ID + " POST /api/v1/orders/{orderId}/cancel";
        assertThat(rows(orderId).subList(before, rows(orderId).size())).containsExactly(
            "ORDER#" + orderId + " PAID>CANCELLED BUYER_CANCELLED" + source,
            "DELIVERY#" + deliveries.get(0).getId() + " READY>CANCELLED BUYER_CANCELLED" + source,
            "DELIVERY#" + deliveries.get(1).getId() + " READY>CANCELLED BUYER_CANCELLED" + source);
    }

    @Test
    @DisplayName("결제 실패 · 재고 차감 실패 · 결제 마감 만료 취소: 주문 PENDING_PAYMENT>CANCELLED 1줄(사유 · 이벤트 또는 스케줄러)")
    void cancelBySystem() {
        Long failed = place(false).orderId();
        Long outOfStock = place(false).orderId();
        Long expired = place(false).orderId();
        jdbcTemplate.update("update orders.orders set payment_due_at = ? where id = ?",
            LocalDateTime.now().minusMinutes(1), expired);

        orderService.cancelForPaymentFailure(failed);
        orderService.cancelForPaymentFailure(failed);
        orderService.cancelForStockDeductionFailure(new StockDeductionFailedCommand(outOfStock, List.of()));
        orderService.cancelForPaymentExpiry(expired);
        orderService.cancelForPaymentExpiry(expired);

        assertThat(rows(failed)).last()
            .isEqualTo("ORDER#" + failed + " PENDING_PAYMENT>CANCELLED PAYMENT_FAILED EVENT null payment.failed");
        assertThat(rows(outOfStock)).last().isEqualTo(
            "ORDER#" + outOfStock + " PENDING_PAYMENT>CANCELLED OUT_OF_STOCK EVENT null stock.deduction-failed");
        assertThat(rows(expired)).last().isEqualTo(
            "ORDER#" + expired + " PENDING_PAYMENT>CANCELLED PAYMENT_EXPIRED SYSTEM null scheduler:payment-expiry");
        assertThat(rows(failed)).hasSize(2);
        assertThat(rows(outOfStock)).hasSize(2);
        assertThat(rows(expired)).hasSize(2);
    }

    @Test
    @DisplayName("결제 반영 실패: 주문 >PAID · 배송 줄은 되돌려지고 결제 복구 null>PENDING 1줄(실패 코드 · 이벤트)만 남는다")
    void recoveryRecorded() {
        OrderDetail order = place(true);
        breakSeller(order);

        assertThatThrownBy(() -> eventDelivery.invoke(completed(order)))
            .isInstanceOf(DataIntegrityViolationException.class);

        Long recoveryId = recoveryRepository.findByOrderId(order.orderId()).orElseThrow().getId();
        assertThat(rows(order.orderId())).containsExactly(
            "ORDER#" + order.orderId() + " null>PENDING_PAYMENT null BUYER " + BUYER_ID + " POST /api/v1/orders",
            "PAYMENT_RECOVERY#" + recoveryId
                + " null>PENDING DataIntegrityViolationException EVENT null payment.completed");
    }

    @Test
    @DisplayName("결제 반영 재처리: 주문 >PAID · 배송 >READY · 결제 복구 PENDING>RECOVERED 1줄씩(관리자 · 재처리 경로)")
    void recoveryRetried() {
        OrderDetail order = place(true);
        breakSeller(order);
        assertThatThrownBy(() -> eventDelivery.invoke(completed(order)))
            .isInstanceOf(DataIntegrityViolationException.class);
        Long recoveryId = recoveryRepository.findByOrderId(order.orderId()).orElseThrow().getId();

        recoveryRetry.retry(recoveryId, ADMIN_ID);
        fixSeller(order);
        recoveryRetry.retry(recoveryId, ADMIN_ID);
        recoveryRetry.retry(recoveryId, ADMIN_ID);

        String admin = " ADMIN " + ADMIN_ID + " POST /api/v1/admin/orders/recoveries/{id}/retry";
        List<Delivery> deliveries = deliveryRepository.findAllByOrderIdOrderByIdAsc(order.orderId());
        assertThat(rows(order.orderId()).subList(2, rows(order.orderId()).size())).containsExactly(
            "ORDER#" + order.orderId() + " PENDING_PAYMENT>PAID null" + admin,
            "DELIVERY#" + deliveries.get(0).getId() + " null>READY null" + admin,
            "DELIVERY#" + deliveries.get(1).getId() + " null>READY null" + admin,
            "PAYMENT_RECOVERY#" + recoveryId + " PENDING>RECOVERED null" + admin);
    }

    @Test
    @DisplayName("결제 반영 취소로 종료: 주문 PENDING_PAYMENT>CANCELLED(PAYMENT_FAILED) · 결제 복구 PENDING>CANCELLED 1줄씩(관리자)")
    void recoveryCancelled() {
        OrderDetail order = place(true);
        breakSeller(order);
        assertThatThrownBy(() -> eventDelivery.invoke(completed(order)))
            .isInstanceOf(DataIntegrityViolationException.class);
        Long recoveryId = recoveryRepository.findByOrderId(order.orderId()).orElseThrow().getId();

        recoveryService.cancel(recoveryId, ADMIN_ID);
        recoveryService.cancel(recoveryId, ADMIN_ID);

        String admin = " ADMIN " + ADMIN_ID + " POST /api/v1/admin/orders/recoveries/{id}/cancel";
        assertThat(rows(order.orderId()).subList(2, rows(order.orderId()).size())).containsExactly(
            "ORDER#" + order.orderId() + " PENDING_PAYMENT>CANCELLED PAYMENT_FAILED" + admin,
            "PAYMENT_RECOVERY#" + recoveryId + " PENDING>CANCELLED null" + admin);
    }

    @Test
    @DisplayName("재처리 대기 중 주문이 취소되면: 결제 복구 PENDING>CANCELLED 1줄(사유 ORDER_CANCELLED · 취소 주체, #598)이 그때 남고, "
        + "그 뒤 재처리는 409라 줄이 늘지 않는다")
    void retryAfterOrderCancelled() {
        OrderDetail order = place(true);
        breakSeller(order);
        assertThatThrownBy(() -> eventDelivery.invoke(completed(order)))
            .isInstanceOf(DataIntegrityViolationException.class);
        Long recoveryId = recoveryRepository.findByOrderId(order.orderId()).orElseThrow().getId();
        int before = rows(order.orderId()).size();
        orderService.cancelByBuyer(BUYER_ID, order.orderId());

        assertThat(rows(order.orderId()).subList(before, rows(order.orderId()).size())).contains(
            "PAYMENT_RECOVERY#" + recoveryId + " PENDING>CANCELLED ORDER_CANCELLED BUYER " + BUYER_ID
                + " POST /api/v1/orders/{orderId}/cancel");
        int afterCancel = rows(order.orderId()).size();
        assertThatThrownBy(() -> recoveryRetry.retry(recoveryId, ADMIN_ID)).isInstanceOf(BusinessException.class);
        assertThat(rows(order.orderId())).hasSize(afterCancel);
    }

    @Test
    @DisplayName("결제 내용 불일치로 실패하면 결제 복구 null>NOT_RETRYABLE 1줄(사유 코드 · 결제 완료 이벤트)")
    void mismatchRecordedAsNotRetryable() {
        OrderDetail order = place(false);

        assertThatThrownBy(() -> eventDelivery.invoke(completed(order, BUYER_ID + 1)))
            .isInstanceOf(PaymentMismatchException.class);

        Long recoveryId = recoveryRepository.findByOrderId(order.orderId()).orElseThrow().getId();
        assertThat(rows(order.orderId()).subList(1, rows(order.orderId()).size())).containsExactly(
            "PAYMENT_RECOVERY#" + recoveryId + " null>NOT_RETRYABLE member-mismatch EVENT null payment.completed");
    }

    @Test
    @DisplayName("같은 주문이 다시 실패해 시도만 늘면(PENDING 그대로) 결제 복구 줄은 늘지 않는다")
    void secondFailureAddsNoRow() {
        OrderDetail order = place(true);
        breakSeller(order);
        assertThatThrownBy(() -> eventDelivery.invoke(completed(order)))
            .isInstanceOf(DataIntegrityViolationException.class);
        int before = rows(order.orderId()).size();

        assertThatThrownBy(() -> eventDelivery.invoke(completed(order)))
            .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(recoveryRepository.findByOrderId(order.orderId()).orElseThrow().getAttemptCount()).isEqualTo(2);
        assertThat(rows(order.orderId())).hasSize(before);
    }

    @Test
    @DisplayName("재처리 실패: 상태가 바뀔 때(PENDING>NOT_RETRYABLE)만 1줄, 시도 횟수만 늘면 0줄")
    void retryFailureRecordedOnlyOnStatusChange() {
        OrderDetail order = place(true);
        breakSeller(order);
        assertThatThrownBy(() -> eventDelivery.invoke(completed(order)))
            .isInstanceOf(DataIntegrityViolationException.class);
        Long recoveryId = recoveryRepository.findByOrderId(order.orderId()).orElseThrow().getId();

        recoveryService.recordRetryFailure(recoveryId, ADMIN_ID, new IllegalStateException("retryable"));
        recoveryService.recordRetryFailure(recoveryId, ADMIN_ID,
            new PaymentMismatchException(order.orderId(), PaymentMismatchException.Reason.MEMBER_MISMATCH, "test"));
        recoveryService.recordRetryFailure(recoveryId, ADMIN_ID, new IllegalStateException("retryable"));

        assertThat(rows(order.orderId()).subList(2, rows(order.orderId()).size())).containsExactly(
            "PAYMENT_RECOVERY#" + recoveryId + " PENDING>NOT_RETRYABLE member-mismatch ADMIN " + ADMIN_ID
                + " POST /api/v1/admin/orders/recoveries/{id}/retry");
    }

    @Test
    @DisplayName("실패 코드가 결제 복구 기록의 최대 길이(100자)여도 기록 1건 · 이력 1줄(사유 칼럼이 같은 길이)")
    void longestFailureCode() {
        OrderDetail order = place(false);

        RuntimeException error = new
            LongFailureCodeXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX();
        String failureCode = error.getClass().getSimpleName();

        recoveryService.recordFailure(MarkPaidCommands.matching(order, BUYER_ID, PAYMENT_ID), error);

        assertThat(failureCode).hasSize(100);
        assertThat(recoveryRepository.findByOrderId(order.orderId()).orElseThrow().getFailureCode())
            .isEqualTo(failureCode);
        assertThat(rows(order.orderId())).hasSize(2).last().asString()
            .contains(" null>PENDING " + failureCode + " EVENT ");
    }

    // 클래스 단순 이름이 실패 코드가 된다(PaymentFailures.failureCode). 이름 길이 100자
    private static final class
        LongFailureCodeXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
        extends RuntimeException {
    }

    private List<String> rows(Long orderId) {
        return historyRepository.findAllByOrderIdOrderByOccurredAtAscIdAsc(orderId).stream()
            .map(OrderStatusHistoryIntegrationTest::row)
            .toList();
    }

    private static String row(OrderStatusHistory history) {
        return history.getTargetType() + "#" + history.getTargetId() + " " + history.getFromStatus() + ">"
            + history.getToStatus() + " " + history.getReason() + " " + history.getActorType() + " "
            + history.getActorId() + " " + history.getSource();
    }

    private static PlaceOrderCommand command(boolean twoSellers) {
        List<PlaceOrderCommand.Item> items = twoSellers
            ? List.of(new PlaceOrderCommand.Item(1L, null, 1, 25_000L),
                new PlaceOrderCommand.Item(2L, null, 1, 30_000L))
            : List.of(new PlaceOrderCommand.Item(1L, null, 1, 25_000L));
        return new PlaceOrderCommand(items,
            new PlaceOrderCommand.ShippingAddress("홍길동", "010-0000-0000", "12345", "서울시 테스트로 1"));
    }

    private OrderDetail place(boolean twoSellers) {
        cartService.addItem(BUYER_ID, 1L, null, 1);
        return orderService.place(BUYER_ID, command(twoSellers));
    }

    private Long placeAndPay(boolean twoSellers) {
        OrderDetail order = place(twoSellers);
        eventDelivery.invoke(completed(order));
        return order.orderId();
    }

    private void breakSeller(OrderDetail order) {
        jdbcTemplate.update("update orders.order_item set seller_id = null where order_id = ? and seller_id = ?",
            order.orderId(), OTHER_SELLER_ID);
    }

    private void fixSeller(OrderDetail order) {
        jdbcTemplate.update("update orders.order_item set seller_id = ? where order_id = ? and seller_id is null",
            OTHER_SELLER_ID, order.orderId());
    }

    private static PaymentCompletedEvent completed(OrderDetail order) {
        return completed(order, BUYER_ID);
    }

    private static PaymentCompletedEvent completed(OrderDetail order, Long payerId) {
        LocalDateTime now = LocalDateTime.now();
        return new PaymentCompletedEvent(UUID.randomUUID(), now, new PaymentCompletedEvent.Data(
            PAYMENT_ID, order.orderId(), payerId, order.totalAmount(),
            order.items().stream().map(item -> new PaymentCompletedEvent.Item(item.orderItemId(), item.amount()))
                .toList(), now));
    }
}
