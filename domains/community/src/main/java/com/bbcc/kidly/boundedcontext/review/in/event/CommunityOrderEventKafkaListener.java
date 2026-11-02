package com.bbcc.kidly.boundedcontext.review.in.event;

import com.bbcc.kidly.boundedcontext.review.in.config.CommunityKafkaListenerConfig;
import com.bbcc.kidly.shared.order.event.OrderItemPurchaseConfirmedEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * kidly.order.events에서 order-item.purchase-confirmed를 받아 Spring 수신과 같은 처리를 부른다. 다른 주문 이벤트는 건너뛴다.
 * 처리가 실패하면 예외를 그대로 던진다(재시도 → DLT). 본문을 읽지 못하면(eventType 헤더 없음 · 깨진 JSON) 재시도 없이 DLT로 간다.
 */
@Component
public class CommunityOrderEventKafkaListener {

    public static final String ID = "communityOrderEventKafkaListener";
    private static final String PURCHASE_CONFIRMED = "order-item.purchase-confirmed";

    private final JsonMapper jsonMapper;
    private final CommunityOrderEventListener orderEventListener;

    public CommunityOrderEventKafkaListener(JsonMapper jsonMapper, CommunityOrderEventListener orderEventListener) {
        this.jsonMapper = jsonMapper;
        this.orderEventListener = orderEventListener;
    }

    @KafkaListener(id = ID, topics = CommunityKafkaListenerConfig.ORDER_EVENTS,
        groupId = CommunityKafkaListenerConfig.GROUP, containerFactory = CommunityKafkaListenerConfig.FACTORY)
    public void on(ConsumerRecord<String, String> record) {
        if (PURCHASE_CONFIRMED.equals(CommunityKafkaMessages.eventType(record))) {
            orderEventListener.onPurchaseConfirmed(
                jsonMapper.readValue(record.value(), OrderItemPurchaseConfirmedEvent.class));
        }
    }
}
