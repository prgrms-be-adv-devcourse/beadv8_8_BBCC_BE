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

/**
 * kidly.order.events에서 order.created를 받아 재고 차감을, order.cancelled를 받아 재고 복구를 맡긴다.
 * 판매자 부분 취소 order-item.cancelled를 받으면 취소한 주문상품의 재고 복구를 맡긴다.
 * 상품이 받지 않는 종류의 이벤트는 건너뛴다.
 * 트랜잭션은 서비스가 부르는 조각 빈이 각자 연다. 서비스는 바깥 트랜잭션 없이 불러야 하므로 리스너에도 붙이지 않는다.
 */
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
            // 헤더가 없으면 어떤 이벤트인지 알 수 없어 재시도 없이 DLT로 보낸다
            case null -> throw new IllegalArgumentException("eventType 헤더가 없습니다");
            // 상품이 받지 않는 종류는 건너뛴다
            default -> { }
        }
    }
}
