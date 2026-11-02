package com.bbcc.kidly.boundedcontext.product.in.inventory;

import com.bbcc.kidly.boundedcontext.product.app.inventory.InventoryRestorationService;
import com.bbcc.kidly.global.eventpublisher.DomainEventListener;
import com.bbcc.kidly.shared.order.event.OrderItemCancelledEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 판매자 부분 취소(주문상품 취소됨)를 받아 취소한 주문상품의 재고 복구를 맡긴다.
 * Kafka 수신 스위치(kidly.product.kafka.consumer.enabled)가 false일 때만 만든다. 켜져 있으면(기본) ProductOrderKafkaListener만 받는다.
 */
@Component
@ConditionalOnProperty(name = "kidly.product.kafka.consumer.enabled", havingValue = "false")
@RequiredArgsConstructor
public class ProductOrderItemCancelledEventListener {

    private final InventoryRestorationService inventoryRestorationService;

    @DomainEventListener
    public void on(OrderItemCancelledEvent event) {
        inventoryRestorationService.restore(event);
    }
}
