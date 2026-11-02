package com.bbcc.kidly.boundedcontext.product.in.inventory;

import com.bbcc.kidly.boundedcontext.product.app.inventory.InventoryDeductionService;
import com.bbcc.kidly.global.eventpublisher.DomainEventListener;
import com.bbcc.kidly.shared.order.event.OrderCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 주문 생성됨을 받아 재고 차감을 맡긴다.
 * Kafka 수신 스위치(kidly.product.kafka.consumer.enabled)가 false일 때만 만든다. 켜져 있으면(기본) ProductOrderKafkaListener만 받는다.
 */
@Component
@ConditionalOnProperty(name = "kidly.product.kafka.consumer.enabled", havingValue = "false")
@RequiredArgsConstructor
public class ProductOrderEventListener {

    private final InventoryDeductionService inventoryDeductionService;

    @DomainEventListener
    public void on(OrderCreatedEvent event) {
        inventoryDeductionService.deduct(event);
    }
}
