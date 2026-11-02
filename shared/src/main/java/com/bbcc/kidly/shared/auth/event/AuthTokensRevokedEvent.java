package com.bbcc.kidly.shared.auth.event;

import com.bbcc.kidly.shared.auth.PrincipalType;
import com.bbcc.kidly.shared.event.DomainEvent;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 주체가 지금까지 받은 Access Token을 무효화했다(회원 탈퇴 · 정지 · 관리자 해제 · 판매자 정지 · Refresh Token 재사용 감지).
 * {@code revokedAt}과 같은 초까지 발급된 Access Token을 거절한다. 같은 주체에 여러 번 오면 가장 늦은 {@code revokedAt}을 남긴다.
 * Gateway가 받아 (주체 종류, 주체 ID)별 무효화 목록을 채운다.
 */
public record AuthTokensRevokedEvent(UUID eventId, LocalDateTime occurredAt, Data data) implements DomainEvent {

    public record Data(PrincipalType principalType, Long principalId, LocalDateTime revokedAt) {
    }

    @Override
    public String aggregateId() {
        return String.valueOf(data.principalId());
    }
}
