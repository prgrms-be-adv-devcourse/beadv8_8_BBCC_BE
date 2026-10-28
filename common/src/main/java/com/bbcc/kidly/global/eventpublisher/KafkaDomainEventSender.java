package com.bbcc.kidly.global.eventpublisher;

import com.bbcc.kidly.shared.event.DomainEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * 토픽은 eventType, 키는 correlationId로 보낸다. 같은 키(주문 ID 등)의 이벤트는 한 파티션에 들어가 순서가 지켜진다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaDomainEventSender implements DomainEventSender {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public void send(DomainEvent event) {
        try {
            kafkaTemplate.send(event.eventType(), event.correlationId(), event)
                .whenComplete((result, e) -> {
                    if (e != null) {
                        logFailure(event, e);
                    }
                });
        } catch (RuntimeException e) {
            logFailure(event, e);
        }
    }

    private void logFailure(DomainEvent event, Throwable e) {
        log.error("이벤트를 Kafka로 보내지 못했습니다. eventType={} eventId={} key={} reason={}",
            event.eventType(), event.eventId(), event.correlationId(), e.getMessage());
    }
}
