package com.bbcc.kidly.boundedcontext.seller.out.outbox;

import com.bbcc.kidly.shared.event.DomainEvent;
import java.time.LocalDateTime;
import java.util.UUID;

public record SellerEventEnvelope(
    UUID eventId,
    String eventType,
    int schemaVersion,
    LocalDateTime occurredAt,
    String producer,
    String aggregateId,
    String correlationId,
    Object data) {

    public static SellerEventEnvelope from(DomainEvent event) {
        return new SellerEventEnvelope(event.eventId(), event.eventType(), event.schemaVersion(),
            event.occurredAt(), event.producer(), event.aggregateId(), event.correlationId(), event.data());
    }
}
