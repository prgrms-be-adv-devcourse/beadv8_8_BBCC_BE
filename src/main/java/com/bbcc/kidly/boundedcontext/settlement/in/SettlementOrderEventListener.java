package com.bbcc.kidly.boundedcontext.settlement.in;

import com.bbcc.kidly.boundedcontext.settlement.app.SettlementItemService;
import com.bbcc.kidly.global.eventpublisher.DomainEventListener;
import com.bbcc.kidly.shared.order.event.OrderItemPurchaseConfirmedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 주문상품 구매확정을 받아 정산항목 저장을 맡긴다.
 */
@Component
@RequiredArgsConstructor
public class SettlementOrderEventListener {

    private final SettlementItemService settlementItemService;

    @DomainEventListener
    public void on(OrderItemPurchaseConfirmedEvent event) {
        settlementItemService.registerPurchaseConfirmed(event);
    }
}
