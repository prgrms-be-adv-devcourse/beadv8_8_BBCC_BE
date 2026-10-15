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
import com.bbcc.kidly.boundedcontext.order.domain.order.PaymentMismatchException;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * 결제대기 주문과 회원 · 주문상품 구성 · 주문상품 금액 · 총액 중 하나라도 다른 결제 완료가 오면 결제완료로 바꾸지 않는다
 * (결제대기 유지 → 결제 마감 만료로 취소될 대상). 리스너 메서드를 직접 부른다(자체 트랜잭션).
 * 테스트에 @Transactional을 붙이지 않는다.
 */
@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class PaymentMismatchIntegrationTest {

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

    @ParameterizedTest(name = "{0}")
    @EnumSource(PaymentMismatchException.Reason.class)
    @DisplayName("결제가 주문과 다르면 예외 · ERROR 로그(reason 코드), 주문은 결제대기 · 배송 0건 · 장바구니 그대로 · order.cancelled 없음")
    void mismatchKeepsPendingPayment(PaymentMismatchException.Reason reason, CapturedOutput output) {
        cartService.addItem(BUYER_ID, 1L, null, 1);
        cartService.addItem(BUYER_ID, 2L, null, 1);
        OrderDetail order = orderService.place(BUYER_ID, new PlaceOrderCommand(
            List.of(new PlaceOrderCommand.Item(1L, null, 1, 25_000L), new PlaceOrderCommand.Item(2L, null, 1, 30_000L)),
            new PlaceOrderCommand.ShippingAddress("홍길동", "010-0000-0000", "12345", "서울시 테스트로 1")));
        PaymentCompletedEvent event = mismatched(order, reason);

        assertThatThrownBy(() -> paymentEventListener.onPaymentCompleted(event))
            .isInstanceOfSatisfying(PaymentMismatchException.class, e -> assertThat(e.getReason()).isEqualTo(reason));

        assertThat(output).containsPattern("ERROR.*결제 완료 처리 실패: event=payment.completed orderId=" + order.orderId());
        assertThat(output).contains("reason=" + reason.code());
        Order stored = orderRepository.findById(order.orderId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(Order.Status.PENDING_PAYMENT);
        assertThat(stored.getPaymentId()).isNull();
        assertThat(deliveryRepository.findAllByOrderIdOrderByIdAsc(order.orderId())).isEmpty();
        assertThat(cartItemRepository.findAllByMemberIdOrderByIdDesc(BUYER_ID)).hasSize(2);
        assertThat(recorder.events(OrderCancelledEvent.class))
            .filteredOn(cancelled -> cancelled.data().orderId().equals(order.orderId()))
            .isEmpty();
    }

    /**
     * 주문과 그대로 맞는 결제 완료에서 사유 하나만 틀리게 바꾼다.
     */
    private static PaymentCompletedEvent mismatched(OrderDetail order, PaymentMismatchException.Reason reason) {
        List<PaymentCompletedEvent.Item> items = order.items().stream()
            .map(item -> new PaymentCompletedEvent.Item(item.orderItemId(), item.amount()))
            .toList();
        PaymentCompletedEvent.Item first = items.getFirst();
        Long memberId = BUYER_ID;
        long amount = order.totalAmount();
        switch (reason) {
            case MEMBER_MISMATCH -> memberId = BUYER_ID + 1;
            case ITEM_MISSING -> items = List.of(first);
            case ITEM_EXTRA -> items = List.of(first, items.get(1), new PaymentCompletedEvent.Item(9_999L, 0L));
            case ITEM_AMOUNT_MISMATCH -> items = List.of(
                new PaymentCompletedEvent.Item(first.orderItemId(), first.amount() - 1), items.get(1));
            case TOTAL_AMOUNT_MISMATCH -> amount = order.totalAmount() + 1;
            default -> throw new IllegalArgumentException(reason.name());
        }
        LocalDateTime now = LocalDateTime.now();
        return new PaymentCompletedEvent(UUID.randomUUID(), now,
            new PaymentCompletedEvent.Data(500L, order.orderId(), memberId, amount, items, now));
    }
}
