package com.bbcc.kidly.global.eventpublisher;

import com.bbcc.kidly.shared.event.DomainEvent;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Kafka 발행 · 수신 통합 테스트용 이벤트. 키는 실제 이벤트처럼 aggregateId와 다른 correlationId(주문 ID)로 보낸다.
 */
public record KafkaTestEvent(UUID eventId, LocalDateTime occurredAt, Data data) implements DomainEvent {

    public static final String TOPIC = "kafka-test.happened";

    public record Data(Long id, Long orderId, boolean fail) {
    }

    public static KafkaTestEvent of(Long id, Long orderId, boolean fail) {
        return new KafkaTestEvent(UUID.randomUUID(), LocalDateTime.of(2026, 10, 29, 0, 0), new Data(id, orderId, fail));
    }

    @Override
    public String aggregateId() {
        return String.valueOf(data.id());
    }

    @Override
    public String correlationId() {
        return String.valueOf(data.orderId());
    }

    @Override
    public String eventType() {
        return TOPIC;
    }
}
