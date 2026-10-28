package com.bbcc.kidly.boundedcontext.payment.app;

import com.bbcc.kidly.boundedcontext.payment.app.dto.PayOrderCommand;
import com.bbcc.kidly.global.eventpublisher.DomainEventListener;
import com.bbcc.kidly.shared.order.event.OrderPaymentRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * order.payment-requested를 받아 주문을 예치금으로 결제한다. 이벤트 필드가 바뀌면 여기 변환만 고친다.
 */
@Component
@RequiredArgsConstructor
public class OrderPaymentRequestedListener {

    private final OrderPaymentService orderPaymentService;

    @DomainEventListener
    public void on(OrderPaymentRequestedEvent event) {
        OrderPaymentRequestedEvent.Data data = event.data();
        orderPaymentService.pay(new PayOrderCommand(data.orderId(), data.memberId(), data.amount(),
            data.items().stream()
                .map(item -> new PayOrderCommand.Item(item.orderItemId(), item.amount()))
                .toList()));
    }
}
