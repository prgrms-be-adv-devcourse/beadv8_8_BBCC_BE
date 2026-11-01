package com.bbcc.kidly.boundedcontext.order.in.event;

import com.bbcc.kidly.boundedcontext.order.in.config.OrderKafkaListenerConfig;
import com.bbcc.kidly.shared.seller.event.SellerChangedEvent;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * kidly.seller.events에서 seller.changed를 받아 판매자 복사본 처리를 그대로 부른다(#541).
 * 처리는 처리 메서드가 여는 트랜잭션에서 끝나고, 실패하면 예외를 그대로 던져 재시도 → DLT로 보낸다.
 * 같은 메시지를 다시 받아도 늦은 변경만 반영하므로(occurredAt 비교) 결과는 1회분이다.
 */
@Component
@RequiredArgsConstructor
public class SellerEventKafkaListener {

    static final String ID = "orderSellerEventKafkaListener";

    private final JsonMapper jsonMapper;
    private final SellerReplicaEventListener sellerReplicaEventListener;

    @KafkaListener(id = ID, topics = OrderKafkaListenerConfig.SELLER_EVENTS, groupId = OrderKafkaListenerConfig.GROUP,
        containerFactory = OrderKafkaListenerConfig.FACTORY)
    public void on(ConsumerRecord<String, String> record) {
        switch (OrderKafkaMessages.eventType(record)) {
            case "seller.changed" -> sellerReplicaEventListener.onSellerChanged(
                jsonMapper.readValue(record.value(), SellerChangedEvent.class));
            default -> {
                // 주문이 받지 않는 종류
            }
        }
    }
}
