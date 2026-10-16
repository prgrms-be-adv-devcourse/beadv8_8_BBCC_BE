package com.bbcc.kidly.boundedcontext.payment.app;

import com.bbcc.kidly.boundedcontext.payment.app.dto.OrderCancelCommand;
import com.bbcc.kidly.global.eventpublisher.DomainEventListener;
import com.bbcc.kidly.shared.order.event.OrderCancelledEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * order.cancelled를 받아 결제된 주문은 환불하고, 결제 전이면 그 뒤 결제 요청을 거절하게 한다. 이벤트 필드가 바뀌면 여기 변환만 고친다.
 * 처리는 별도 트랜잭션이라 실패하면 처리한 것만 롤백된다. 실패한 취소는 남겨 결제 스케줄러가 다시 처리한다(#350).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCancelledListener {

    private final OrderCancellationService orderCancellationService;
    private final OrderCancellationRetryService orderCancellationRetryService;

    @DomainEventListener
    public void on(OrderCancelledEvent event) {
        OrderCancelledEvent.Data data = event.data();
        OrderCancelCommand command = new OrderCancelCommand(data.orderId(), data.memberId(), data.reason());
        try {
            orderCancellationService.handle(command);
        } catch (RuntimeException e) {
            log.error("주문 취소를 처리하지 못해 다시 처리할 취소로 남깁니다. orderId={}", command.orderId(), e);
            orderCancellationRetryService.remember(command, e);
        }
    }
}
