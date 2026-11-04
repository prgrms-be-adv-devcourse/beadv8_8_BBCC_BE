package com.bbcc.kidly.boundedcontext.auth.out.kafka;

import com.bbcc.kidly.shared.event.DomainEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import tools.jackson.databind.json.JsonMapper;

public final class AuthEventEnvelope {
    private AuthEventEnvelope() {
    }

    public static String toJson(JsonMapper mapper, DomainEvent event) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventId", event.eventId());
        body.put("eventType", event.eventType());
        body.put("schemaVersion", event.schemaVersion());
        body.put("occurredAt", event.occurredAt());
        body.put("producer", event.producer());
        body.put("aggregateId", event.aggregateId());
        body.put("correlationId", event.correlationId());
        body.put("data", event.data());
        return mapper.writeValueAsString(body);
    }
}
