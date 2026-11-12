package com.bbcc.kidly.boundedcontext.activity.app;

import com.bbcc.kidly.shared.event.DomainEvent;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Kafka로 보내는 커뮤니티 이벤트 본문: 공통 칸 7개 + data(JSON 칸 순서는 이 record 순서). 이벤트 record를 그대로 직렬화하면 기본값으로
 * 정해지는 칸(eventType · schemaVersion · producer · aggregateId · correlationId)이 빠지므로 DomainEvent의 값으로 직접 채운다.
 * occurredAt은 이벤트의 LocalDateTime 그대로(오프셋 없음).
 */
record CommunityEventEnvelope(
    UUID eventId,
    String eventType,
    int schemaVersion,
    LocalDateTime occurredAt,
    String producer,
    String aggregateId,
    String correlationId,
    Object data) {

    static CommunityEventEnvelope from(DomainEvent event) {
        return new CommunityEventEnvelope(event.eventId(), event.eventType(), event.schemaVersion(), event.occurredAt(),
            event.producer(), event.aggregateId(), event.correlationId(), event.data());
    }
}
