package com.bbcc.kidly.shared.member.event;

import com.bbcc.kidly.shared.event.DomainEvent;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 회원이 탈퇴했다. 결제 · 추천 · 주문 도메인이 회원 관련 데이터를 정리한다.
 * 자녀 프로필 도메인이 이 이벤트를 받아 자녀를 삭제하고, 자녀마다 {@link ChildChangedEvent}(deleted = true)를 발행한다.
 */
public record MemberWithdrawnEvent(UUID eventId, LocalDateTime occurredAt, Data data) implements DomainEvent {

    public record Data(Long memberId) {
    }

    @Override
    public String aggregateId() {
        return String.valueOf(data.memberId());
    }
}
