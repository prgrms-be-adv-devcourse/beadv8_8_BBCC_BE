package com.bbcc.kidly.shared.member.event;

import com.bbcc.kidly.shared.event.DomainEvent;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 회원이 가입했다. 결제 도메인이 지갑을 만든다.
 */
public record MemberRegisteredEvent(UUID eventId, LocalDateTime occurredAt, Data data) implements DomainEvent {

    public record Data(Long memberId) {
    }

    @Override
    public String aggregateId() {
        return String.valueOf(data.memberId());
    }
}
