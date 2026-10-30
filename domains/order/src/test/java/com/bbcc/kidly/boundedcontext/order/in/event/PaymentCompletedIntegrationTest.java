package com.bbcc.kidly.boundedcontext.order.in.event;

import static com.bbcc.kidly.boundedcontext.order.OrderRows.PLACED;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.BUYER_ID;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.OCCURRED_AT;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.SELLER_ID;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.order.OrderEventDelivery;
import com.bbcc.kidly.boundedcontext.order.OrderRows;
import com.bbcc.kidly.boundedcontext.order.app.cart.CartService;
import com.bbcc.kidly.boundedcontext.order.app.order.OrderService;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.OrderDetail;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.PlaceOrderCommand;
import com.bbcc.kidly.boundedcontext.order.app.port.CartItemRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.ChildReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.DeliveryRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.ProductReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.domain.cart.CartItem;
import com.bbcc.kidly.boundedcontext.order.domain.delivery.Delivery;
import com.bbcc.kidly.boundedcontext.order.domain.order.Order;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaChild;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaProduct;
import com.bbcc.kidly.boundedcontext.order.OrderFlowEventRecorder;
import com.bbcc.kidly.boundedcontext.order.support.IntegrationTest;
import com.bbcc.kidly.shared.order.event.OrderCancelledEvent;
import com.bbcc.kidly.shared.order.event.OrderPaymentRequestedEvent;
import com.bbcc.kidly.shared.payment.event.PaymentCompletedEvent;
import com.bbcc.kidly.shared.product.event.StockDeductedEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 주문 → stock.deducted → payment.completed → 결제완료 · 판매자별 배송 · 장바구니 정리.
 * 주문 모듈 테스트에는 결제 도메인이 없으므로 결제 이벤트는 테스트가 직접 넣는다(#616).
 * 리스너는 발행한 트랜잭션이 커밋된 뒤에 실행되므로 테스트에 @Transactional을 붙이지 않는다.
 */
@IntegrationTest
class PaymentCompletedIntegrationTest {

    private static final Long OTHER_SELLER_ID = 3L;

    @Autowired
    private OrderRows orderRows;

    @Autowired
    private OrderEventDelivery eventDelivery;

    @Autowired
    private OrderService orderService;

    @Autowired
    private CartService cartService;

    @Autowired
    private ProductReplicaRepository productReplicaRepository;

    @Autowired
    private ChildReplicaRepository childReplicaRepository;

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

    @Test
    @DisplayName("결제가 완료되면 결제완료 · 판매자별 배송 · 장바구니 정리, 커밋 뒤 같은 이벤트가 다시 와도 "
        + "주문 · 배송 · 장바구니 · 상태 이력 · 발행 이벤트가 1회분이고 실패로 보관하지 않는다")
    void paidOnce() {
        recorder.clear();
        productReplicaRepository.save(ReplicaProduct.create(ReplicaFixture.onSaleProduct(25_000L, OCCURRED_AT)));
        productReplicaRepository.save(ReplicaProduct.create(new ReplicaProduct.Values(
            2L, 2L, OTHER_SELLER_ID, "상품 B", null, "120", 30_000L, 2L, "ON_SALE", null, false, OCCURRED_AT)));
        childReplicaRepository.save(ReplicaChild.create(ReplicaFixture.child("115.0", false, OCCURRED_AT)));
        cartService.addItem(BUYER_ID, 1L, 1L, 2);
        cartService.addItem(BUYER_ID, 2L, null, 1);
        cartService.addItem(BUYER_ID, 1L, null, 1);

        OrderDetail order = orderService.place(BUYER_ID, new PlaceOrderCommand(
            List.of(new PlaceOrderCommand.Item(1L, 1L, 2, 25_000L), new PlaceOrderCommand.Item(2L, null, 1, 30_000L)),
            new PlaceOrderCommand.ShippingAddress("홍길동", "010-0000-0000", "12345", "서울시 테스트로 1")));
        eventDelivery.deliver(new StockDeductedEvent(UUID.randomUUID(), LocalDateTime.now(),
            new StockDeductedEvent.Data(order.orderId())));

        PaymentCompletedEvent completed = paymentCompleted(order);
        eventDelivery.deliver(completed);

        assertThat(orderRepository.findById(order.orderId())).get()
            .extracting(Order::getStatus).isEqualTo(Order.Status.PAID);
        List<Delivery> deliveries = deliveryRepository.findAllByOrderIdOrderByIdAsc(order.orderId());
        assertThat(deliveries).extracting(Delivery::getSellerId).containsExactly(SELLER_ID, OTHER_SELLER_ID);
        assertThat(deliveries)
            .allSatisfy(delivery -> assertThat(delivery.getStatus()).isEqualTo(Delivery.Status.READY));
        List<CartItem> remaining = cartItemRepository.findAllByMemberIdOrderByIdDesc(BUYER_ID);
        assertThat(remaining).hasSize(1);
        assertThat(remaining.get(0).getOptionId()).isEqualTo(1L);
        assertThat(remaining.get(0).getChildId()).isNull();

        long version = orderRows.versionOf(order.orderId());
        eventDelivery.deliver(completed);

        assertThat(deliveryRepository.findAllByOrderIdOrderByIdAsc(order.orderId())).hasSize(2);
        assertThat(orderRepository.findById(order.orderId())).get()
            .extracting(Order::getStatus).isEqualTo(Order.Status.PAID);
        assertThat(orderRows.versionOf(order.orderId())).as("다시 온 쪽은 주문을 바꾸지 않음").isEqualTo(version);
        assertThat(cartItemRepository.findAllByMemberIdOrderByIdDesc(BUYER_ID)).hasSize(1);
        assertThat(orderRows.historyRows(order.orderId()))
            .containsExactly(PLACED, "ORDER PENDING_PAYMENT>PAID payment.completed",
                "DELIVERY null>READY payment.completed", "DELIVERY null>READY payment.completed");
        assertThat(recorder.events(OrderPaymentRequestedEvent.class))
            .filteredOn(event -> event.data().orderId().equals(order.orderId())).hasSize(1);
        assertThat(recorder.events(OrderCancelledEvent.class))
            .filteredOn(event -> event.data().orderId().equals(order.orderId())).isEmpty();
        assertThat(jdbcTemplate.queryForObject("select count(*) from orders.payment_recovery where order_id = ?",
            Long.class, order.orderId())).as("다시 온 쪽은 실패로 보관하지 않음").isZero();
    }

    private PaymentCompletedEvent paymentCompleted(OrderDetail order) {
        return new PaymentCompletedEvent(UUID.randomUUID(), LocalDateTime.now(), new PaymentCompletedEvent.Data(
            500L, order.orderId(), BUYER_ID, order.totalAmount(),
            order.items().stream()
                .map(item -> new PaymentCompletedEvent.Item(item.orderItemId(), item.amount()))
                .toList(),
            LocalDateTime.now()));
    }


}
