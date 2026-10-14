package com.bbcc.kidly.shared.member.event;

import com.bbcc.kidly.shared.event.DomainEvent;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 회원 정보가 바뀌었다(지금은 닉네임만 바꿀 수 있다). 판매자 도메인이 판매자 신청의 신청 회원 닉네임을 갱신한다.
 * 바뀐 뒤의 전체 값을 담으므로 받는 쪽은 덮어쓰기만 한다. 저장된 값보다 {@code occurredAt}이 늦을 때만 반영한다.
 * 새 회원 정보가 생기면 {@code Data}에 필드를 더한다(추가만 한다).
 */
public record MemberChangedEvent(UUID eventId, LocalDateTime occurredAt, Data data) implements DomainEvent {

    public record Data(Long memberId, String nickname) {
    }

    @Override
    public String aggregateId() {
        return String.valueOf(data.memberId());
    }
}
