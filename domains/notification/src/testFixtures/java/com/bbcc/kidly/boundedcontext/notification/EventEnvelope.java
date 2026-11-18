package com.bbcc.kidly.boundedcontext.notification;

import com.bbcc.kidly.shared.event.DomainEvent;
import java.util.LinkedHashMap;
import java.util.Map;

public final class EventEnvelope {
    private EventEnvelope() {
    }

    public static Map<String, Object> of(DomainEvent event) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventId", event.eventId());
        body.put("eventType", event.eventType());
        body.put("schemaVersion", event.schemaVersion());
        body.put("occurredAt", event.occurredAt());
        body.put("producer", event.producer());
        body.put("aggregateId", event.aggregateId());
        body.put("correlationId", event.correlationId());
        body.put("data", event.data());
        return body;
    }
}
