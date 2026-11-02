package com.bbcc.kidly.boundedcontext.product.in.inventory;

import com.bbcc.kidly.boundedcontext.product.app.inventory.InventoryRestorationService;
import com.bbcc.kidly.global.eventpublisher.DomainEventListener;
import com.bbcc.kidly.shared.order.event.OrderCancelledEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 주문 취소됨을 받아 재고 복구를 맡긴다.
 * Kafka 수신 스위치(kidly.product.kafka.consumer.enabled)가 false일 때만 만든다. 켜져 있으면(기본) ProductOrderKafkaListener만 받는다.
 */
@Component
@ConditionalOnProperty(name = "kidly.product.kafka.consumer.enabled", havingValue = "false")
@RequiredArgsConstructor
public class ProductOrderCancelledEventListener {

    private final InventoryRestorationService inventoryRestorationService;

    @DomainEventListener
    public void on(OrderCancelledEvent event) {
        inventoryRestorationService.restore(event);
    }
}
