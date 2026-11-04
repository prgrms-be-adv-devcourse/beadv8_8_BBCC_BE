package com.bbcc.kidly.boundedcontext.member.out.outbox;

import com.bbcc.kidly.shared.event.DomainEvent;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Kafka로 보내는 회원 이벤트 본문이다. JSON 칸 순서는 이 record의 선언 순서와 같다.
 * 이벤트 record를 그대로 직렬화하면 DomainEvent의 기본 메서드 값(eventType 등 5칸)이 빠지므로 DomainEvent의 값으로 직접 채운다.
 */
record MemberEventEnvelope(
    UUID eventId,
    String eventType,
    int schemaVersion,
    LocalDateTime occurredAt,
    String producer,
    String aggregateId,
    String correlationId,
    Object data) {

    static MemberEventEnvelope from(DomainEvent event) {
        return new MemberEventEnvelope(event.eventId(), event.eventType(), event.schemaVersion(),
            event.occurredAt(), event.producer(), event.aggregateId(), event.correlationId(), event.data());
    }
}
