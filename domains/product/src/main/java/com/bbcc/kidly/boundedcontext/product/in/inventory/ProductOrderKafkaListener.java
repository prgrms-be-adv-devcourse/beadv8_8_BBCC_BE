package com.bbcc.kidly.boundedcontext.product.in.inventory;

import com.bbcc.kidly.boundedcontext.product.app.inventory.InventoryDeductionService;
import com.bbcc.kidly.boundedcontext.product.app.inventory.InventoryRestorationService;
import com.bbcc.kidly.boundedcontext.product.in.ProductKafkaListenerConfig;
import com.bbcc.kidly.shared.order.event.OrderCancelledEvent;
import com.bbcc.kidly.shared.order.event.OrderCreatedEvent;
import com.bbcc.kidly.shared.order.event.OrderItemCancelledEvent;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class ProductOrderKafkaListener {

    public static final String ID = "productOrderKafkaListener";

    private final InventoryDeductionService inventoryDeductionService;
    private final InventoryRestorationService inventoryRestorationService;
    private final JsonMapper jsonMapper;

    @KafkaListener(id = ID, topics = ProductKafkaListenerConfig.ORDER_EVENTS,
        groupId = ProductKafkaListenerConfig.GROUP, containerFactory = "productKafkaListenerContainerFactory",
        autoStartup = ProductKafkaListenerConfig.AUTO_STARTUP)
    public void on(ConsumerRecord<String, String> record) {
        switch (ProductKafkaListenerConfig.header(record, "eventType")) {
            case "order.created" ->
                inventoryDeductionService.deduct(jsonMapper.readValue(record.value(), OrderCreatedEvent.class));
            case "order.cancelled" ->
                inventoryRestorationService.restore(jsonMapper.readValue(record.value(), OrderCancelledEvent.class));
            case "order-item.cancelled" -> inventoryRestorationService.restore(
                jsonMapper.readValue(record.value(), OrderItemCancelledEvent.class));
            case null -> throw new IllegalArgumentException("eventType 헤더가 없습니다");
            default -> { }
        }
    }
}
