package com.bbcc.kidly.shared.member.event;

import com.bbcc.kidly.shared.event.DomainEvent;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 회원이 닉네임을 바꿨다. 판매자 도메인이 판매자 신청의 신청 회원 닉네임을 갱신한다.
 * 저장된 값보다 {@code occurredAt}이 늦을 때만 반영한다.
 */
public record MemberNicknameChangedEvent(UUID eventId, LocalDateTime occurredAt, Data data) implements DomainEvent {

    public record Data(Long memberId, String nickname) {
    }

    @Override
    public String aggregateId() {
        return String.valueOf(data.memberId());
    }
}
