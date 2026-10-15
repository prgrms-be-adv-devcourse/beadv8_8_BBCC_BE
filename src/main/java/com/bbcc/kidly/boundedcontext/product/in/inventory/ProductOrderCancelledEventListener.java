package com.bbcc.kidly.boundedcontext.product.in.inventory;

import com.bbcc.kidly.boundedcontext.product.app.inventory.InventoryRestorationService;
import com.bbcc.kidly.global.eventpublisher.DomainEventListener;
import com.bbcc.kidly.shared.order.event.OrderCancelledEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 주문 취소됨을 받아 재고 복구를 맡긴다.
 */
@Component
@RequiredArgsConstructor
public class ProductOrderCancelledEventListener {

    private final InventoryRestorationService inventoryRestorationService;

    @DomainEventListener
    public void on(OrderCancelledEvent event) {
        inventoryRestorationService.restore(event);
    }
}
