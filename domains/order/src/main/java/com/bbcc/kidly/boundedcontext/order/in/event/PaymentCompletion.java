package com.bbcc.kidly.boundedcontext.order.in.event;

import com.bbcc.kidly.boundedcontext.order.app.OrderActor;
import com.bbcc.kidly.boundedcontext.order.app.cart.CartService;
import com.bbcc.kidly.boundedcontext.order.app.delivery.DeliveryService;
import com.bbcc.kidly.boundedcontext.order.app.order.OrderService;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.MarkPaidCommand;
import java.util.function.Consumer;
import java.util.function.Predicate;
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
     * 결제 완료 이벤트로 반영한다(상태 이력의 주체 = payment.completed 이벤트).
     *
     * @return 이번에 결제완료로 바꿨으면 true. 주문이 없거나 결제대기가 아니면 false(배송 · 장바구니는 건드리지 않음)
     */
    public boolean apply(MarkPaidCommand paid) {
        return apply(paid, orderService::markPaid, deliveryService::createFor);
    }

    /**
     * 이벤트가 아닌 주체(관리자 재처리)가 반영한다. 상태 이력에 그 주체를 남긴다(#442).
     */
    public boolean apply(MarkPaidCommand paid, OrderActor actor) {
        return apply(paid, command -> orderService.markPaid(command, actor),
            orderId -> deliveryService.createFor(orderId, actor));
    }

    private boolean apply(MarkPaidCommand paid, Predicate<MarkPaidCommand> markPaid, Consumer<Long> createDeliveries) {
        if (!markPaid.test(paid)) {
            return false;
        }
        createDeliveries.accept(paid.orderId());
        cartService.clearOrdered(paid.orderId());
        return true;
    }
}
