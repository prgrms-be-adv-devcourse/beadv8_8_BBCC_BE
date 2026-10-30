package com.bbcc.kidly.boundedcontext.settlement.in;

import com.bbcc.kidly.boundedcontext.settlement.app.SettlementItemService;
import com.bbcc.kidly.shared.order.event.OrderItemPurchaseConfirmedEvent;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * kidly.order.events에서 order-item.purchase-confirmed를 받아 정산 대상으로 쌓는다. 주문의 다른 종류는 건너뛴다.
 * 같은 주문상품을 다시 받아도 정산 대상은 하나다(SettlementItemService).
 * 스위치(kidly.settlement.kafka.consumer.enabled)가 꺼져 있으면 시작하지 않고, 그때는 Spring 리스너(SettlementOrderEventListener)가 받는다.
 */
@Component
@RequiredArgsConstructor
public class SettlementOrderKafkaListener {

    public static final String ID = "settlementOrderKafkaListener";

    private final SettlementItemService settlementItemService;
    private final JsonMapper jsonMapper;

    @KafkaListener(id = ID, topics = SettlementKafkaListenerConfig.ORDER_EVENTS,
        groupId = SettlementKafkaListenerConfig.GROUP, containerFactory = "settlementKafkaListenerContainerFactory",
        autoStartup = SettlementKafkaListenerConfig.AUTO_STARTUP)
    public void on(ConsumerRecord<String, String> record) {
        switch (SettlementKafkaListenerConfig.eventType(record)) {
            case "order-item.purchase-confirmed" -> settlementItemService.register(
                jsonMapper.readValue(record.value(), OrderItemPurchaseConfirmedEvent.class).data());
            default -> { }  // 정산이 받지 않는 종류
        }
    }
}
