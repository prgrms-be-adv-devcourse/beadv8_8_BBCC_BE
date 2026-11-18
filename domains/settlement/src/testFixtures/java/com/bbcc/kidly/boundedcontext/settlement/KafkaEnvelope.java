package com.bbcc.kidly.boundedcontext.settlement;

import com.bbcc.kidly.shared.event.DomainEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import tools.jackson.databind.json.JsonMapper;

/**
 * 보내는 쪽 본문과 같은 모양(공통 칸 7개 + data)으로 이벤트를 JSON 문자열로 만든다.
 * record를 그대로 직렬화하면 eventType 등 record에 없는 칸이 빠진다.
 */
final class KafkaEnvelope {

    private KafkaEnvelope() {
    }

    @SuppressWarnings("unchecked")
    static String of(JsonMapper jsonMapper, DomainEvent event) {
        Map<String, Object> fields = jsonMapper.convertValue(event, Map.class);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventId", fields.get("eventId"));
        body.put("eventType", event.eventType());
        body.put("schemaVersion", event.schemaVersion());
        body.put("occurredAt", fields.get("occurredAt"));
        body.put("producer", event.producer());
        body.put("aggregateId", event.aggregateId());
        body.put("correlationId", event.correlationId());
        body.put("data", fields.get("data"));
        return jsonMapper.writeValueAsString(body);
    }
}
