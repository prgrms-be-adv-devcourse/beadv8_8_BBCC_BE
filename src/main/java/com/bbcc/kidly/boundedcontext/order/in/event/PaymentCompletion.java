package com.bbcc.kidly.boundedcontext.order.in.event;

import com.bbcc.kidly.boundedcontext.order.app.cart.CartService;
import com.bbcc.kidly.boundedcontext.order.app.delivery.DeliveryService;
import com.bbcc.kidly.boundedcontext.order.app.order.OrderService;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.MarkPaidCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 결제 완료 반영: 결제완료 → 판매자별 주문 만들기 → 장바구니 정리. 부른 쪽 트랜잭션 하나로 묶인다.
 * 결제 완료 리스너와 관리자 재처리(#353)가 같은 순서를 쓴다. 기능(주문 · 배송 · 장바구니)끼리는 서로 부르지 않으므로
 * 여러 기능을 잇는 이 순서는 in에 둔다.
 */
@Component
@RequiredArgsConstructor
public class PaymentCompletion {

    private final OrderService orderService;
    private final DeliveryService deliveryService;
    private final CartService cartService;

    /**
     * @return 이번에 결제완료로 바꿨으면 true. 주문이 없거나 결제대기가 아니면 false(배송 · 장바구니는 건드리지 않음)
     */
    public boolean apply(MarkPaidCommand paid) {
        if (!orderService.markPaid(paid)) {
            return false;
        }
        deliveryService.createFor(paid.orderId());
        cartService.clearOrdered(paid.orderId());
        return true;
    }
}
