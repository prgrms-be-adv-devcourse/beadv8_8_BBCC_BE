package com.bbcc.kidly.boundedcontext.payment.out.kafka;

import com.bbcc.kidly.shared.event.DomainEvent;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Kafka로 보내는 결제 이벤트 본문: 공통 칸 7개(기획서 6.2) + data. JSON 칸 순서는 이 record의 순서다.
 * 이벤트 record를 그대로 직렬화하면 기본값으로 정해지는 칸(eventType · schemaVersion · producer · aggregateId ·
 * correlationId)이 빠지므로 DomainEvent의 값으로 직접 채운다.
 */
record PaymentEventEnvelope(
    UUID eventId,
    String eventType,
    int schemaVersion,
    LocalDateTime occurredAt,
    String producer,
    String aggregateId,
    String correlationId,
    Object data) {

    static PaymentEventEnvelope from(DomainEvent event) {
        return new PaymentEventEnvelope(event.eventId(), event.eventType(), event.schemaVersion(), event.occurredAt(),
            event.producer(), event.aggregateId(), event.correlationId(), event.data());
    }
}
