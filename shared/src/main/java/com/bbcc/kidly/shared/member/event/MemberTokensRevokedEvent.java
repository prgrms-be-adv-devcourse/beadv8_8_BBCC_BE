package com.bbcc.kidly.shared.member.event;

import com.bbcc.kidly.shared.event.DomainEvent;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 회원이 지금까지 받은 Access Token을 무효화했다(탈퇴 · 정지 · 권한 회수 · Refresh Token 재사용 감지 · 판매자 정지).
 * {@code revokedAt}과 같은 초까지 발급된 Access Token을 거절한다. 같은 회원에 여러 번 오면 가장 늦은 {@code revokedAt}을 남긴다.
 * 4단계 Gateway가 받아 무효화 캐시를 채운다. 지금은 받는 곳이 없다.
 */
public record MemberTokensRevokedEvent(UUID eventId, LocalDateTime occurredAt, Data data) implements DomainEvent {

    public record Data(Long memberId, LocalDateTime revokedAt) {
    }

    @Override
    public String aggregateId() {
        return String.valueOf(data.memberId());
    }
}
