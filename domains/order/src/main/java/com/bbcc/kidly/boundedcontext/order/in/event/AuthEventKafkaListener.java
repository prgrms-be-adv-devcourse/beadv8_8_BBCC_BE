package com.bbcc.kidly.boundedcontext.order.in.event;

import com.bbcc.kidly.boundedcontext.order.in.config.OrderKafkaListenerConfig;
import com.bbcc.kidly.shared.auth.event.ChildAccountChangedEvent;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * kidly.auth.events에서 child-account.changed를 받아 자녀 계정 복사본 처리를 그대로 부른다(#906). Kafka로만 받는다(판매자 · 자녀와 같음).
 * 처리는 처리 메서드가 여는 트랜잭션에서 끝나고, 실패하면 예외를 그대로 던져 재시도 → DLT로 보낸다.
 * 같은 메시지를 다시 받아도 삭제는 한 번 · 생성은 늦을 때만 반영하므로 결과는 1회분이다. 토큰 무효화(auth.tokens-revoked)는 받지 않는다.
 */
@Component
@RequiredArgsConstructor
public class AuthEventKafkaListener {

    static final String ID = "orderAuthEventKafkaListener";

    private final JsonMapper jsonMapper;
    private final ChildAccountEventListener childAccountEventListener;

    @KafkaListener(id = ID, topics = OrderKafkaListenerConfig.AUTH_EVENTS, groupId = OrderKafkaListenerConfig.GROUP,
        containerFactory = OrderKafkaListenerConfig.FACTORY)
    public void on(ConsumerRecord<String, String> record) {
        switch (OrderKafkaMessages.eventType(record)) {
            case "child-account.changed" -> childAccountEventListener.onChildAccountChanged(
                jsonMapper.readValue(record.value(), ChildAccountChangedEvent.class));
            default -> {
                // 주문이 받지 않는 종류
            }
        }
    }
}
