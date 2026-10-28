package com.bbcc.kidly.shared.member.event;

import com.bbcc.kidly.shared.event.DomainEvent;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 회원이 가입했다. 결제 도메인이 지갑을 만든다. 이메일은 가입 때 받고 지금은 바꿀 수 없어 이 값을 복사해 둘 수 있다.
 */
public record MemberRegisteredEvent(UUID eventId, LocalDateTime occurredAt, Data data) implements DomainEvent {

    public record Data(Long memberId, String email) {
    }

    @Override
    public String aggregateId() {
        return String.valueOf(data.memberId());
    }
}
