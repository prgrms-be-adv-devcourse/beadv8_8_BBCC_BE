package com.bbcc.kidly.boundedcontext.order.in.event;

import static com.bbcc.kidly.boundedcontext.order.OrderRows.PLACED;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.BUYER_ID;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.OCCURRED_AT;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.order.OrderEventDelivery;
import com.bbcc.kidly.boundedcontext.order.OrderRows;
import com.bbcc.kidly.boundedcontext.order.app.order.OrderService;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.OrderDetail;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.PlaceOrderCommand;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.ProductReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.domain.order.Order;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaProduct;
import com.bbcc.kidly.boundedcontext.order.in.scheduler.OrderScheduler;
import com.bbcc.kidly.boundedcontext.support.IntegrationTest;
import com.bbcc.kidly.shared.order.event.OrderCancelledEvent;
import com.bbcc.kidly.shared.order.event.OrderPaymentRequestedEvent;
import com.bbcc.kidly.shared.product.domain.StockDeductionFailureCode;
import com.bbcc.kidly.shared.product.event.StockDeductedEvent;
import com.bbcc.kidly.shared.product.event.StockDeductionFailedEvent;
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
 * stock.deduction-failed를 받아 주문이 OUT_OF_STOCK으로 취소되고 주문상품별 사유가 저장되는지,
 * 중복 · 만료 뒤 도착은 그대로인지, 취소 뒤 늦게 온 stock.deducted가 결제를 요청하지 않는지.
 * 리스너는 발행한 트랜잭션이 커밋된 뒤에 실행되므로 테스트에 @Transactional을 붙이지 않고, 저장 값은 JDBC로 다시 읽는다.
 */
@IntegrationTest
@RecordApplicationEvents
@ExtendWith(OutputCaptureExtension.class)
class StockDeductionFailedIntegrationTest {

    private static final Long SECOND_OPTION_ID = 2L;

    @Autowired
    private OrderRows orderRows;

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
    private OrderRepository orderRepository;

    @BeforeEach
    void setUp() {
        productReplicaRepository.save(ReplicaProduct.create(ReplicaFixture.onSaleProduct(25_000L, OCCURRED_AT)));
        productReplicaRepository.save(ReplicaProduct.create(
            ReplicaFixture.option(SECOND_OPTION_ID, "ON_SALE", "ON_SALE", false, OCCURRED_AT)));
    }

    @Test
    @DisplayName("재고 차감 실패를 받으면 OUT_OF_STOCK으로 취소되고, 사유가 온 주문상품에만 사유가 저장되며 order.cancelled가 한 번 발행된다")
    void cancelsAndStoresItemReasons() {
        OrderDetail order = place();
        Long first = order.items().get(0).orderItemId();

        publishFailed(order.orderId(), first, StockDeductionFailureCode.OPTION_NOT_ON_SALE);

        assertCancelled(order.orderId(), Order.CancelReason.OUT_OF_STOCK);
        assertThat(storedReasons(order.orderId())).containsExactly("OPTION_NOT_ON_SALE", null);
        List<OrderCancelledEvent> cancelled = events.stream(OrderCancelledEvent.class).toList();
        assertThat(cancelled).hasSize(1);
        assertThat(cancelled.get(0).data().reason()).isEqualTo("OUT_OF_STOCK");
        assertThat(cancelled.get(0).data().items()).hasSize(2);
    }

    @Test
    @DisplayName("재고 차감 실패 한 건의 로그는 서비스 결과 한 줄(주문 취소 · reason=OUT_OF_STOCK)뿐이고 리스너 수신 로그는 없다")
    void logsOneResultLine(CapturedOutput output) {
        OrderDetail order = place();

        publishFailed(order.orderId(), order.items().get(0).orderItemId(), StockDeductionFailureCode.OUT_OF_STOCK);

        assertThat(output).containsPattern("INFO.*주문 취소: orderId=" + order.orderId() + " reason=OUT_OF_STOCK");
        assertThat(output.getAll().lines()
            .filter(line -> line.contains("주문 취소: orderId=" + order.orderId() + " ")).count()).isEqualTo(1);
        assertThat(output).doesNotContain("재고 차감 실패 수신");
    }

    @Test
    @DisplayName("같은 재고 차감 실패가 두 번 와도 취소 · order.cancelled는 한 번이고 저장된 사유는 그대로")
    void duplicateEventCancelsOnce() {
        OrderDetail order = place();
        Long first = order.items().get(0).orderItemId();

        publishFailed(order.orderId(), first, StockDeductionFailureCode.OUT_OF_STOCK);
        long version = orderRows.versionOf(order.orderId());
        publishFailed(order.orderId(), first, StockDeductionFailureCode.PRODUCT_NOT_ON_SALE);

        assertCancelled(order.orderId(), Order.CancelReason.OUT_OF_STOCK);
        assertThat(storedReasons(order.orderId())).containsExactly("OUT_OF_STOCK", null);
        assertThat(events.stream(OrderCancelledEvent.class).toList()).hasSize(1);
        assertThat(orderRows.versionOf(order.orderId())).as("다시 온 쪽은 주문을 바꾸지 않음").isEqualTo(version);
        assertThat(orderRows.historyRows(order.orderId()))
            .containsExactly(PLACED, "ORDER PENDING_PAYMENT>CANCELLED OUT_OF_STOCK stock.deduction-failed");
        assertThat(eventFailureCount(order.orderId())).as("다시 온 쪽은 실패로 보관하지 않음").isZero();
    }

    @Test
    @DisplayName("결제 마감 만료로 먼저 취소된 뒤 온 재고 차감 실패는 무시된다(사유 PAYMENT_EXPIRED · 주문상품 사유 없음)")
    void ignoredAfterExpiry() {
        OrderDetail order = place();
        jdbcTemplate.update("update orders.orders set payment_due_at = ? where id = ?",
            LocalDateTime.now().minusMinutes(1), order.orderId());
        orderScheduler.expireOverduePayments();

        publishFailed(order.orderId(), order.items().get(0).orderItemId(), StockDeductionFailureCode.OUT_OF_STOCK);

        assertCancelled(order.orderId(), Order.CancelReason.PAYMENT_EXPIRED);
        assertThat(storedReasons(order.orderId())).containsExactly(null, null);
        assertThat(events.stream(OrderCancelledEvent.class).toList()).hasSize(1);
    }

    @Test
    @DisplayName("재고 차감 실패로 취소된 뒤 늦게 온 재고 차감됨은 결제를 요청하지 않는다")
    void lateStockDeductedDoesNotRequestPayment() {
        OrderDetail order = place();
        publishFailed(order.orderId(), order.items().get(0).orderItemId(), StockDeductionFailureCode.OUT_OF_STOCK);

        eventDelivery.deliver(new StockDeductedEvent(UUID.randomUUID(), LocalDateTime.now(),
            new StockDeductedEvent.Data(order.orderId())));

        assertCancelled(order.orderId(), Order.CancelReason.OUT_OF_STOCK);
        assertThat(events.stream(OrderPaymentRequestedEvent.class).toList()).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
            "select payment_requested_at from orders.orders where id = ?", LocalDateTime.class, order.orderId()))
            .isNull();
    }

    private OrderDetail place() {
        return orderService.place(BUYER_ID, new PlaceOrderCommand(
            List.of(new PlaceOrderCommand.Item(1L, null, 2, 25_000L),
                new PlaceOrderCommand.Item(SECOND_OPTION_ID, null, 1, 25_000L)),
            new PlaceOrderCommand.ShippingAddress("홍길동", "010-0000-0000", "12345", "서울시 테스트로 1")));
    }

    private void publishFailed(Long orderId, Long orderItemId, StockDeductionFailureCode reason) {
        eventDelivery.deliver(new StockDeductionFailedEvent(UUID.randomUUID(), LocalDateTime.now(),
            new StockDeductionFailedEvent.Data(orderId, reason,
                List.of(new StockDeductionFailedEvent.Failure(orderItemId, reason)))));
    }

    private List<String> storedReasons(Long orderId) {
        return jdbcTemplate.queryForList(
            "select unavailable_reason from orders.order_item where order_id = ? order by id", String.class, orderId);
    }

    private long eventFailureCount(Long orderId) {
        return jdbcTemplate.queryForObject(
            "select count(*) from orders.order_event_failure where order_id = ?", Long.class, orderId);
    }

    private void assertCancelled(Long orderId, Order.CancelReason reason) {
        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(Order.Status.CANCELLED);
        assertThat(order.getCancelReason()).isEqualTo(reason);
    }

}
