package com.bbcc.kidly.boundedcontext.order.in.event;

import com.bbcc.kidly.boundedcontext.order.app.order.OrderService;
import com.bbcc.kidly.global.eventpublisher.DomainEventListener;
import com.bbcc.kidly.shared.product.event.StockDeductedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * stock.deducted를 받아 그 주문의 결제를 요청한다. 이벤트 필드가 바뀌면 여기 변환만 고친다.
 */
@Component
@RequiredArgsConstructor
public class StockEventListener {

    private final OrderService orderService;

    @DomainEventListener
    public void onStockDeducted(StockDeductedEvent event) {
        orderService.requestPayment(toOrderId(event));
    }

    private Long toOrderId(StockDeductedEvent event) {
        return event.data().orderId();
    }
}
