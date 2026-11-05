package com.bbcc.kidly.shared.member.event;

import com.bbcc.kidly.shared.event.DomainEvent;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 회원이 정지됐다. 알림 도메인이 회원에게 정지 사유를 안내한다.
 */
public record MemberSuspendedEvent(UUID eventId, LocalDateTime occurredAt, Data data) implements DomainEvent {

    public record Data(Long memberId, String reason, LocalDateTime suspendedAt) {
    }

    @Override
    public String aggregateId() {
        return String.valueOf(data.memberId());
    }
}
