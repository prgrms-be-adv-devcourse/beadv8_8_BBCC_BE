package com.bbcc.kidly.boundedcontext.payment.app;

import com.bbcc.kidly.boundedcontext.payment.app.dto.OrderCancelCommand;
import com.bbcc.kidly.global.eventpublisher.DomainEventListener;
import com.bbcc.kidly.shared.order.event.OrderCancelledEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * order.cancelled를 받아 결제된 주문은 환불하고, 결제 전이면 그 뒤 결제 요청을 거절하게 한다. 이벤트 필드가 바뀌면 여기 변환만 고친다.
 */
@Component
@RequiredArgsConstructor
public class OrderCancelledListener {

    private final OrderCancellationService orderCancellationService;

    @DomainEventListener
    public void on(OrderCancelledEvent event) {
        OrderCancelledEvent.Data data = event.data();
        orderCancellationService.handle(new OrderCancelCommand(data.orderId(), data.memberId(), data.reason()));
    }
}
