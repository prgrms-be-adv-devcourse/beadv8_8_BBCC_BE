package com.bbcc.kidly.boundedcontext.product.in.inventory;

import com.bbcc.kidly.boundedcontext.product.app.inventory.InventoryDeductionService;
import com.bbcc.kidly.boundedcontext.product.app.inventory.InventoryRestorationService;
import com.bbcc.kidly.boundedcontext.product.in.ProductKafkaListenerConfig;
import com.bbcc.kidly.shared.order.event.OrderCancelledEvent;
import com.bbcc.kidly.shared.order.event.OrderCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * kidly.order.events에서 order.created를 받아 재고 차감을, order.cancelled를 받아 재고 복구를 맡긴다.
 * 상품이 받지 않는 종류의 이벤트는 건너뛴다.
 * 트랜잭션은 서비스가 연다.
 */
@Component
@RequiredArgsConstructor
public class ProductOrderKafkaListener {

    private final InventoryDeductionService inventoryDeductionService;
    private final InventoryRestorationService inventoryRestorationService;
    private final JsonMapper jsonMapper;

    @KafkaListener(id = "productOrderKafkaListener", topics = ProductKafkaListenerConfig.ORDER_EVENTS,
        groupId = ProductKafkaListenerConfig.GROUP, containerFactory = "productKafkaListenerContainerFactory",
        autoStartup = ProductKafkaListenerConfig.AUTO_STARTUP)
    public void on(ConsumerRecord<String, String> record) {
        switch (ProductKafkaListenerConfig.header(record, "eventType")) {
            case "order.created" ->
                inventoryDeductionService.deduct(jsonMapper.readValue(record.value(), OrderCreatedEvent.class));
            case "order.cancelled" ->
                inventoryRestorationService.restore(jsonMapper.readValue(record.value(), OrderCancelledEvent.class));
            // 상품이 받지 않는 종류는 건너뛴다
            default -> { }
        }
    }
}
