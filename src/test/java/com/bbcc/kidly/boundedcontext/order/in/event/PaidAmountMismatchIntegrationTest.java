package com.bbcc.kidly.boundedcontext.order.in.event;

import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.OCCURRED_AT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.order.OrderFlowEventRecorder;
import com.bbcc.kidly.boundedcontext.order.app.cart.CartService;
import com.bbcc.kidly.boundedcontext.order.app.order.OrderService;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.OrderDetail;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.PlaceOrderCommand;
import com.bbcc.kidly.boundedcontext.order.app.port.CartItemRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.DeliveryRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.ProductReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.domain.order.Order;
import com.bbcc.kidly.boundedcontext.order.domain.order.PaidAmountMismatchException;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaProduct;
import com.bbcc.kidly.shared.order.event.OrderCancelledEvent;
import com.bbcc.kidly.shared.payment.event.PaymentCompletedEvent;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 주문 총액과 다른 금액의 결제 완료가 오면 결제완료로 바꾸지 않는다(결제대기 유지 → 결제 마감 만료로 취소될 대상).
 * 리스너 메서드를 직접 부른다(자체 트랜잭션). 테스트에 @Transactional을 붙이지 않는다.
 */
@IntegrationTest
class PaidAmountMismatchIntegrationTest {

    private static final Long BUYER_ID = 5_000L;
    private static final Long OTHER_SELLER_ID = 3L;

    @Autowired
    private PaymentEventListener paymentEventListener;

    @Autowired
    private OrderService orderService;

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
    private OrderFlowEventRecorder recorder;

    @BeforeEach
    void setUp() {
        // 테스트마다 주문 ID가 1부터 다시 시작해(RESTART IDENTITY) 다른 테스트의 같은 orderId 이벤트가 섞이지 않게 비운다
        recorder.clear();
        productReplicaRepository.save(ReplicaProduct.create(ReplicaFixture.onSaleProduct(25_000L, OCCURRED_AT)));
        productReplicaRepository.save(ReplicaProduct.create(new ReplicaProduct.Values(
            2L, 2L, OTHER_SELLER_ID, "상품 B", null, "120", 30_000L, 2L, "ON_SALE", null, false, OCCURRED_AT)));
    }

    @Test
    @DisplayName("결제 금액이 주문 총액과 다르면 예외, 주문은 결제대기 · 배송 0건 · 장바구니 그대로 · order.cancelled 없음")
    void mismatchedAmountKeepsPendingPayment() {
        cartService.addItem(BUYER_ID, 1L, null, 1);
        cartService.addItem(BUYER_ID, 2L, null, 1);
        OrderDetail order = orderService.place(BUYER_ID, new PlaceOrderCommand(
            List.of(new PlaceOrderCommand.Item(1L, null, 1, 25_000L), new PlaceOrderCommand.Item(2L, null, 1, 30_000L)),
            new PlaceOrderCommand.ShippingAddress("홍길동", "010-0000-0000", "12345", "서울시 테스트로 1")));
        PaymentCompletedEvent event = new PaymentCompletedEvent(UUID.randomUUID(), LocalDateTime.now(),
            new PaymentCompletedEvent.Data(500L, order.orderId(), BUYER_ID, order.totalAmount() + 1, List.of(),
                LocalDateTime.now()));

        assertThatThrownBy(() -> paymentEventListener.onPaymentCompleted(event))
            .isInstanceOf(PaidAmountMismatchException.class);

        Order stored = orderRepository.findById(order.orderId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(Order.Status.PENDING_PAYMENT);
        assertThat(stored.getPaymentId()).isNull();
        assertThat(deliveryRepository.findAllByOrderIdOrderByIdAsc(order.orderId())).isEmpty();
        assertThat(cartItemRepository.findAllByMemberIdOrderByIdDesc(BUYER_ID)).hasSize(2);
        assertThat(recorder.events(OrderCancelledEvent.class))
            .filteredOn(cancelled -> cancelled.data().orderId().equals(order.orderId()))
            .isEmpty();
    }
}
