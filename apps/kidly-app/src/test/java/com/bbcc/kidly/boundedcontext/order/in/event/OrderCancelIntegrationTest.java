package com.bbcc.kidly.boundedcontext.order.in.event;

import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.BUYER_ID;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.OCCURRED_AT;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.order.OrderEventDelivery;
import com.bbcc.kidly.boundedcontext.order.app.order.OrderService;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.OrderDetail;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.PlaceOrderCommand;
import com.bbcc.kidly.boundedcontext.order.app.port.ChildReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.DeliveryRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderStatusHistoryRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.ProductReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.domain.order.Order;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaChild;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaProduct;
import com.bbcc.kidly.boundedcontext.order.in.scheduler.OrderScheduler;
import com.bbcc.kidly.shared.order.event.OrderCancelledEvent;
import com.bbcc.kidly.shared.payment.domain.PaymentFailureCode;
import com.bbcc.kidly.shared.payment.event.PaymentCompletedEvent;
import com.bbcc.kidly.shared.payment.event.PaymentFailedEvent;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

/**
 * 결제 실패 · 결제 마감 만료로 주문이 취소되고 order.cancelled가 한 번 발행되는지, 만료 뒤 온 결제 완료는 무시되는지.
 * 리스너는 발행한 트랜잭션이 커밋된 뒤에 실행되므로 테스트에 @Transactional을 붙이지 않는다.
 * 스케줄러는 테스트 프로필에서 저절로 돌지 않게 주기를 아주 길게 두고, 메서드를 직접 부른다.
 */
@IntegrationTest
@RecordApplicationEvents
@ExtendWith(OutputCaptureExtension.class)
class OrderCancelIntegrationTest {

    @Autowired
    private OrderEventDelivery eventDelivery;

    @Autowired
    private ApplicationEvents events;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderScheduler orderScheduler;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ProductReplicaRepository productReplicaRepository;

    @Autowired
    private ChildReplicaRepository childReplicaRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private DeliveryRepository deliveryRepository;

    @Autowired
    private OrderStatusHistoryRepository historyRepository;

    @BeforeEach
    void setUp() {
        productReplicaRepository.save(ReplicaProduct.create(ReplicaFixture.onSaleProduct(25_000L, OCCURRED_AT)));
        childReplicaRepository.save(ReplicaChild.create(ReplicaFixture.child("115.0", false, OCCURRED_AT)));
    }

    @Test
    @DisplayName("결제 실패 이벤트를 받으면 PAYMENT_FAILED로 취소되고 order.cancelled가 한 번 발행된다")
    void cancelsOnPaymentFailure() {
        OrderDetail order = place();

        eventDelivery.deliver(new PaymentFailedEvent(UUID.randomUUID(), LocalDateTime.now(),
            new PaymentFailedEvent.Data(
                500L, order.orderId(), BUYER_ID, order.totalAmount(), PaymentFailureCode.INSUFFICIENT_BALANCE,
                LocalDateTime.now())));

        assertCancelled(order.orderId(), Order.CancelReason.PAYMENT_FAILED);
        List<OrderCancelledEvent> cancelled = events.stream(OrderCancelledEvent.class).toList();
        assertThat(cancelled).hasSize(1);
        assertThat(cancelled.get(0).data().orderId()).isEqualTo(order.orderId());
        assertThat(cancelled.get(0).data().reason()).isEqualTo("PAYMENT_FAILED");
        assertThat(cancelled.get(0).data().items()).hasSize(1);
        assertThat(cancelled.get(0).data().items().get(0).orderItemId())
            .isEqualTo(order.items().get(0).orderItemId());
        assertThat(cancelled.get(0).data().items().get(0).quantity()).isEqualTo(2);
    }

    @Test
    @DisplayName("같은 결제 실패 이벤트가 두 번 와도 취소 1번 · order.cancelled 1건 · 사유 PAYMENT_FAILED 그대로 · 취소 이력 1줄")
    void duplicatePaymentFailureCancelsOnce() {
        OrderDetail order = place();
        PaymentFailedEvent failed = new PaymentFailedEvent(UUID.randomUUID(), LocalDateTime.now(),
            new PaymentFailedEvent.Data(500L, order.orderId(), BUYER_ID, order.totalAmount(),
                PaymentFailureCode.INSUFFICIENT_BALANCE, LocalDateTime.now()));

        eventDelivery.deliver(failed);
        long version = versionOf(order.orderId());
        eventDelivery.deliver(failed);

        assertCancelled(order.orderId(), Order.CancelReason.PAYMENT_FAILED);
        assertThat(versionOf(order.orderId())).as("두 번째는 주문을 바꾸지 않음").isEqualTo(version);
        List<OrderCancelledEvent> cancelled = events.stream(OrderCancelledEvent.class).toList();
        assertThat(cancelled).hasSize(1);
        assertThat(cancelled.get(0).data().reason()).isEqualTo("PAYMENT_FAILED");
        assertThat(cancelRows(order.orderId()))
            .containsExactly("PENDING_PAYMENT>CANCELLED PAYMENT_FAILED payment.failed");
    }

    @Test
    @DisplayName("결제 실패 한 건의 로그는 서비스 결과 한 줄(주문 취소 · reason=PAYMENT_FAILED)뿐이고 리스너 수신 로그는 없다")
    void paymentFailureLogsOneResultLine(CapturedOutput output) {
        OrderDetail order = place();

        eventDelivery.deliver(new PaymentFailedEvent(UUID.randomUUID(), LocalDateTime.now(),
            new PaymentFailedEvent.Data(
                500L, order.orderId(), BUYER_ID, order.totalAmount(), PaymentFailureCode.INSUFFICIENT_BALANCE,
                LocalDateTime.now())));

        assertThat(output).containsPattern("INFO.*주문 취소: orderId=" + order.orderId() + " reason=PAYMENT_FAILED");
        assertThat(output.getAll().lines()
            .filter(line -> line.contains("주문 취소: orderId=" + order.orderId() + " ")).count()).isEqualTo(1);
        assertThat(output).doesNotContain("결제 실패 수신");
    }

    @Test
    @DisplayName("결제 마감이 지난 결제대기 주문은 스케줄러가 PAYMENT_EXPIRED로 취소하고 order.cancelled를 발행한다")
    void expiresOverdueOrder() {
        OrderDetail order = place();
        makeOverdue(order.orderId());

        orderScheduler.expireOverduePayments();

        assertCancelled(order.orderId(), Order.CancelReason.PAYMENT_EXPIRED);
        List<OrderCancelledEvent> cancelled = events.stream(OrderCancelledEvent.class).toList();
        assertThat(cancelled).hasSize(1);
        assertThat(cancelled.get(0).data().reason()).isEqualTo("PAYMENT_EXPIRED");
    }

    @Test
    @DisplayName("결제 마감 만료 스케줄러를 두 번 실행해도 취소 1번 · order.cancelled 1건 · 취소 이력 1줄")
    void expiryRunTwiceCancelsOnce() {
        OrderDetail order = place();
        makeOverdue(order.orderId());

        orderScheduler.expireOverduePayments();
        long version = versionOf(order.orderId());
        orderScheduler.expireOverduePayments();

        assertCancelled(order.orderId(), Order.CancelReason.PAYMENT_EXPIRED);
        assertThat(versionOf(order.orderId())).as("두 번째는 주문을 바꾸지 않음").isEqualTo(version);
        assertThat(events.stream(OrderCancelledEvent.class).toList()).hasSize(1);
        assertThat(cancelRows(order.orderId()))
            .containsExactly("PENDING_PAYMENT>CANCELLED PAYMENT_EXPIRED scheduler:payment-expiry");
    }

    @Test
    @DisplayName("마감이 지나지 않은 결제대기 주문은 스케줄러가 취소하지 않는다")
    void keepsNotOverdueOrder() {
        OrderDetail order = place();

        orderScheduler.expireOverduePayments();

        assertThat(orderRepository.findById(order.orderId())).get()
            .extracting(Order::getStatus).isEqualTo(Order.Status.PENDING_PAYMENT);
        assertThat(events.stream(OrderCancelledEvent.class).toList()).isEmpty();
    }

    @Test
    @DisplayName("조회 조건을 거치지 않고 만료 취소를 바로 불러도, 마감 전 주문은 도메인이 막아 그대로다")
    void domainKeepsNotOverdueOrder() {
        OrderDetail order = place();

        orderService.cancelForPaymentExpiry(order.orderId());

        assertThat(orderRepository.findById(order.orderId())).get()
            .extracting(Order::getStatus).isEqualTo(Order.Status.PENDING_PAYMENT);
        assertThat(events.stream(OrderCancelledEvent.class).toList()).isEmpty();
    }

    @Test
    @DisplayName("만료로 취소된 뒤 온 결제 완료 이벤트는 반영하지 않고(상태 그대로 · 배송 없음) 환불 대상 WARN을 한 줄 남긴다")
    void ignoresPaymentCompletedAfterExpiry(CapturedOutput output) {
        OrderDetail order = place();
        makeOverdue(order.orderId());
        orderScheduler.expireOverduePayments();
        long version = versionOf(order.orderId());
        List<String> historyBefore = historyRows(order.orderId());

        eventDelivery.deliver(new PaymentCompletedEvent(UUID.randomUUID(), LocalDateTime.now(),
            new PaymentCompletedEvent.Data(
                500L, order.orderId(), BUYER_ID, order.totalAmount(), List.of(), LocalDateTime.now())));

        assertCancelled(order.orderId(), Order.CancelReason.PAYMENT_EXPIRED);
        assertThat(deliveryRepository.findAllByOrderIdOrderByIdAsc(order.orderId())).isEmpty();
        assertThat(versionOf(order.orderId())).as("늦게 온 결제 완료는 주문을 바꾸지 않음").isEqualTo(version);
        assertThat(historyRows(order.orderId())).isEqualTo(historyBefore).noneMatch(row -> row.endsWith(">PAID"));
        assertThat(events.stream(OrderCancelledEvent.class).toList()).hasSize(1);
        assertThat(jdbcTemplate.queryForObject("select count(*) from orders.payment_recovery where order_id = ?",
            Long.class, order.orderId())).as("취소된 주문의 결제 완료는 실패로 보관하지 않음").isZero();
        assertThat(lines(output, "WARN", "reason=paid-after-cancel orderId=" + order.orderId()
            + " paymentId=500 amount=" + order.totalAmount() + " cancelReason=PAYMENT_EXPIRED")).isEqualTo(1);
        assertThat(lines(output, "ERROR", "reason=paid-after-cancel")).isEqualTo(0);
    }

    @Test
    @DisplayName("취소된 주문에 결제 ID · 결제 시각 없는 결제 완료가 오면 사유별 한 줄씩(환불 대상 WARN · 결제 ID 없음 ERROR · 결제 시각 없음 ERROR)")
    void paidAfterCancelWithMissingValuesLogsEach(CapturedOutput output) {
        OrderDetail order = place();
        makeOverdue(order.orderId());
        orderScheduler.expireOverduePayments();

        eventDelivery.deliver(new PaymentCompletedEvent(UUID.randomUUID(), LocalDateTime.now(),
            new PaymentCompletedEvent.Data(
                null, order.orderId(), BUYER_ID, order.totalAmount(), List.of(), null)));

        assertCancelled(order.orderId(), Order.CancelReason.PAYMENT_EXPIRED);
        assertThat(lines(output, "WARN", "reason=paid-after-cancel orderId=" + order.orderId() + " paymentId=null"))
            .isEqualTo(1);
        assertThat(lines(output, "ERROR", "reason=missing-payment-id orderId=" + order.orderId())).isEqualTo(1);
        assertThat(lines(output, "ERROR", "reason=missing-paid-at orderId=" + order.orderId())).isEqualTo(1);
    }

    private long lines(CapturedOutput output, String level, String text) {
        return output.getAll().lines()
            .filter(line -> line.contains(" " + level + " ") && line.contains("event=payment.completed " + text))
            .count();
    }

    private OrderDetail place() {
        return orderService.place(BUYER_ID, new PlaceOrderCommand(
            List.of(new PlaceOrderCommand.Item(1L, 1L, 2, 25_000L)),
            new PlaceOrderCommand.ShippingAddress("홍길동", "010-0000-0000", "12345", "서울시 테스트로 1")));
    }

    private void makeOverdue(Long orderId) {
        jdbcTemplate.update("update orders.orders set payment_due_at = ? where id = ?",
            LocalDateTime.now().minusMinutes(1), orderId);
    }

    private long versionOf(Long orderId) {
        return jdbcTemplate.queryForObject("select version from orders.orders where id = ?", Long.class, orderId);
    }

    // 주문의 상태 이력 전체를 "이전>다음"으로
    private List<String> historyRows(Long orderId) {
        return historyRepository.findAllByOrderIdOrderByOccurredAtAscIdAsc(orderId).stream()
            .map(history -> history.getFromStatus() + ">" + history.getToStatus())
            .toList();
    }

    // 주문의 상태 이력 중 취소 줄만 "이전>다음 사유 출처"로
    private List<String> cancelRows(Long orderId) {
        return historyRepository.findAllByOrderIdOrderByOccurredAtAscIdAsc(orderId).stream()
            .filter(history -> "CANCELLED".equals(String.valueOf(history.getToStatus())))
            .map(history -> history.getFromStatus() + ">" + history.getToStatus() + " " + history.getReason() + " "
                + history.getSource())
            .toList();
    }

    private void assertCancelled(Long orderId, Order.CancelReason reason) {
        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(Order.Status.CANCELLED);
        assertThat(order.getCancelReason()).isEqualTo(reason);
    }

}
