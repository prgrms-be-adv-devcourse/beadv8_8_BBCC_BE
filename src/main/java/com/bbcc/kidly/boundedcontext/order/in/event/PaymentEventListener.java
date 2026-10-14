package com.bbcc.kidly.boundedcontext.order.in.event;

import com.bbcc.kidly.boundedcontext.order.app.cart.CartService;
import com.bbcc.kidly.boundedcontext.order.app.delivery.DeliveryService;
import com.bbcc.kidly.boundedcontext.order.app.order.OrderService;
import com.bbcc.kidly.global.eventpublisher.DomainEventListener;
import com.bbcc.kidly.shared.payment.event.PaymentCompletedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * payment.completed를 받아 결제완료 → 판매자별 주문 만들기 → 장바구니 정리를 차례로 맡긴다.
 * 리스너 트랜잭션 하나로 묶여 중간에 실패하면 모두 되돌린다. 이벤트 필드가 바뀌면 여기 변환만 고친다.
 */
@Component
@RequiredArgsConstructor
public class PaymentEventListener {

    private final OrderService orderService;
    private final DeliveryService deliveryService;
    private final CartService cartService;

    @DomainEventListener
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        Paid paid = toPaid(event);
        if (!orderService.markPaid(paid.orderId(), paid.paymentId())) {
            return;
        }
        deliveryService.createFor(paid.orderId());
        cartService.clearOrdered(paid.orderId());
    }

    /**
     * 주문 ID · 결제 ID만 쓴다. 결제 시각은 주문이 지금 시각으로 정하고, 금액 · 주문상품은 비교하지 않는다.
     */
    private Paid toPaid(PaymentCompletedEvent event) {
        return new Paid(event.data().orderId(), event.data().paymentId());
    }

    private record Paid(Long orderId, Long paymentId) {
    }
}
