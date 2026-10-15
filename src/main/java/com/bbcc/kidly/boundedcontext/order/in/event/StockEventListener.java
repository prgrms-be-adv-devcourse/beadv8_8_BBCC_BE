package com.bbcc.kidly.boundedcontext.order.in.event;

import com.bbcc.kidly.boundedcontext.order.app.order.OrderService;
import com.bbcc.kidly.global.eventpublisher.DomainEventListener;
import com.bbcc.kidly.shared.product.event.StockDeductedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * stock.deducted를 받아 그 주문의 결제를 요청한다. 이벤트 필드가 바뀌면 여기 변환만 고친다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StockEventListener {

    private final OrderService orderService;

    /**
     * 주문 ID가 없으면 찾을 주문이 없으므로 WARN만 남기고 무시한다.
     */
    @DomainEventListener
    public void onStockDeducted(StockDeductedEvent event) {
        Long orderId = toOrderId(event);
        if (orderId == null) {
            log.warn("재고 이벤트 무시: event=stock.deducted reason=missing-order-id");
            return;
        }
        orderService.requestPayment(orderId);
    }

    private Long toOrderId(StockDeductedEvent event) {
        return event.data() == null ? null : event.data().orderId();
    }
}
