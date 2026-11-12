package com.bbcc.kidly.shared.childprofile.event;

import com.bbcc.kidly.shared.event.DomainEvent;
import java.time.LocalDateTime;
import java.util.UUID;

public record ChildGrowthRecordDeletedEvent(UUID eventId, LocalDateTime occurredAt, Data data) implements DomainEvent {
    public record Data(Long growthRecordId, Long childId) { }

    @Override
    public String aggregateId() {
        return String.valueOf(data.childId());
    }
}
