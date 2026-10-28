package com.bbcc.kidly.boundedcontext.product.in.inventory;

import com.bbcc.kidly.boundedcontext.product.app.inventory.InventoryDeductionService;
import com.bbcc.kidly.global.eventpublisher.DomainEventListener;
import com.bbcc.kidly.shared.order.event.OrderCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 주문 생성됨을 받아 재고 차감을 맡긴다.
 */
@Component
@RequiredArgsConstructor
public class ProductOrderEventListener {

    private final InventoryDeductionService inventoryDeductionService;

    @DomainEventListener
    public void on(OrderCreatedEvent event) {
        inventoryDeductionService.deduct(event);
    }
}
