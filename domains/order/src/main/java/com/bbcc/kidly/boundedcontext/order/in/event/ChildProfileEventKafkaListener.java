package com.bbcc.kidly.boundedcontext.order.in.event;

import com.bbcc.kidly.shared.childprofile.event.ChildChangedEvent;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * kidly.childprofile.events에서 child.changed를 받아 자녀 복사본 처리를 그대로 부른다(#541).
 * 처리는 처리 메서드의 트랜잭션(REQUIRES_NEW)에서 끝나고, 실패하면 예외를 그대로 던져 재시도 → DLT로 보낸다.
 * 같은 메시지를 다시 받아도 늦은 변경만 반영하므로(occurredAt 비교) 결과는 1회분이다.
 */
@Component
@RequiredArgsConstructor
public class ChildProfileEventKafkaListener {

    static final String ID = "orderChildProfileEventKafkaListener";

    private final JsonMapper jsonMapper;
    private final ChildProfileEventListener childProfileEventListener;

    @KafkaListener(id = ID, topics = OrderKafkaListenerConfig.CHILDPROFILE_EVENTS,
        groupId = OrderKafkaListenerConfig.GROUP, containerFactory = OrderKafkaListenerConfig.FACTORY)
    public void on(ConsumerRecord<String, String> record) {
        switch (OrderKafkaMessages.eventType(record)) {
            case "child.changed" -> childProfileEventListener.onChildChanged(
                jsonMapper.readValue(record.value(), ChildChangedEvent.class));
            default -> {
                // 주문이 받지 않는 종류
            }
        }
    }
}
