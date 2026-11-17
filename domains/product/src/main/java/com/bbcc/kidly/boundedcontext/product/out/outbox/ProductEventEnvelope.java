package com.bbcc.kidly.boundedcontext.product.out.outbox;

import com.bbcc.kidly.shared.event.DomainEvent;
import java.time.LocalDateTime;
import java.util.UUID;

record ProductEventEnvelope(
    UUID eventId,
    String eventType,
    int schemaVersion,
    LocalDateTime occurredAt,
    String producer,
    String aggregateId,
    String correlationId,
    Object data) {

    static ProductEventEnvelope from(DomainEvent event) {
        return new ProductEventEnvelope(event.eventId(), event.eventType(), event.schemaVersion(), event.occurredAt(),
            event.producer(), event.aggregateId(), event.correlationId(), event.data());
    }
}
