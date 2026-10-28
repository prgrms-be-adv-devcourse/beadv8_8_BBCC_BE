package com.bbcc.kidly.boundedcontext.recommend.in.purchase;

import com.bbcc.kidly.boundedcontext.recommend.app.purchase.PurchaseRecordService;
import com.bbcc.kidly.global.eventpublisher.DomainEventListener;
import com.bbcc.kidly.shared.order.event.OrderItemPurchaseConfirmedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 주문상품 구매확정을 받아 구매 기록 저장을 맡긴다.
 */
@Component
@RequiredArgsConstructor
public class RecommendOrderEventListener {

    private final PurchaseRecordService purchaseRecordService;

    @DomainEventListener
    public void on(OrderItemPurchaseConfirmedEvent event) {
        purchaseRecordService.recordPurchaseConfirmed(event);
    }
}
