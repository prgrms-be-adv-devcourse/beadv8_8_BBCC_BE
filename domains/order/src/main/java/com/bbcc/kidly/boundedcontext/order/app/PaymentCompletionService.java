package com.bbcc.kidly.boundedcontext.order.app;

import com.bbcc.kidly.boundedcontext.cart.api.CartApi;
import com.bbcc.kidly.boundedcontext.delivery.api.DeliveryActor;
import com.bbcc.kidly.boundedcontext.delivery.api.DeliveryApi;
import com.bbcc.kidly.boundedcontext.order.app.order.OrderService;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.MarkPaidCommand;
import java.util.function.Consumer;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결제 완료 반영: 결제완료 → 판매자별 주문 만들기 → 장바구니 정리를 한 트랜잭션으로 묶는다(중간에 실패하면 모두 되돌림).
 * 결제 완료 리스너와 관리자 재처리(#353)가 같은 순서를 쓴다. 기능(주문 · 배송 · 장바구니)끼리는 서로 부르지 않으므로
 * 여러 기능을 잇는 이 유스케이스는 app 바로 아래에 둔다(#532). 배송 · 장바구니는 각 컨텍스트의 api로 부르고 이 트랜잭션에 합류한다(#617).
 */
@Service
@RequiredArgsConstructor
public class PaymentCompletionService {

    private final OrderService orderService;
    private final DeliveryApi deliveryApi;
    private final CartApi cartApi;

    /**
     * 결제 완료 이벤트로 반영한다(상태 이력의 주체 = payment.completed 이벤트). 받는 쪽 트랜잭션이 있든 없든 새 트랜잭션에서
     * 커밋까지 끝내므로, 커밋 단계 실패(동시 변경 등)도 본문 실패처럼 부른 쪽에 예외로 돌아온다.
     *
     * @return 이번에 결제완료로 바꿨으면 true. 주문이 없거나 결제대기가 아니면 false(배송 · 장바구니는 건드리지 않음)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean applyForEvent(MarkPaidCommand paid) {
        return apply(paid, orderService::markPaid, deliveryApi::createFor);
    }

    /**
     * 이벤트가 아닌 주체(관리자 재처리)가 반영한다. 상태 이력에 그 주체를 남긴다(#442).
     * 재처리 트랜잭션에 합류해 기록의 복구 상태와 함께 커밋된다.
     */
    @Transactional
    public boolean apply(MarkPaidCommand paid, OrderActor actor) {
        return apply(paid, command -> orderService.markPaid(command, actor),
            orderId -> deliveryApi.createFor(orderId,
                new DeliveryActor(actor.type().name(), actor.id(), actor.source())));
    }

    private boolean apply(MarkPaidCommand paid, Predicate<MarkPaidCommand> markPaid, Consumer<Long> createDeliveries) {
        if (!markPaid.test(paid)) {
            return false;
        }
        createDeliveries.accept(paid.orderId());
        cartApi.clearOrdered(paid.orderId());
        return true;
    }
}
