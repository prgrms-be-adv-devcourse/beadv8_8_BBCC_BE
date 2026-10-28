package com.bbcc.kidly.shared.payment.event;

import com.bbcc.kidly.shared.event.DomainEvent;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DomainEventTest에서 producer 기본값을 확인하려고 shared.{도메인}.event 패키지에 둔 테스트용 이벤트.
 */
public record SampleCreatedEvent(UUID eventId, LocalDateTime occurredAt, Data data)
    implements DomainEvent {

    public record Data(Long sampleId) {
    }

    @Override
    public String aggregateId() {
        return String.valueOf(data.sampleId());
    }
}
