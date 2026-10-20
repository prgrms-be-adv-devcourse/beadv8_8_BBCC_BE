package com.bbcc.kidly.boundedcontext.member.domain;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 회원의 Access Token 무효화 기준 시각. 이 시각과 같은 초까지 발급된 토큰을 거절한다.
 * iat가 초 단위라 같은 초에 발급된 토큰도 거절한다(밀리초가 더 늦어도).
 */
public record AccessTokenRevocationCutoff(Instant revokedAt) {

    // 도메인은 시스템 시간대의 LocalDateTime, JWT는 Instant라 시스템 존으로 바꾼다
    public static AccessTokenRevocationCutoff of(LocalDateTime revokedAt) {
        return new AccessTokenRevocationCutoff(revokedAt.atZone(ZoneId.systemDefault()).toInstant());
    }

    // 보존 기간이 지나면 이 기준으로 막을 토큰이 남아 있지 않다
    public boolean isExpired(Instant now, Duration retention) {
        return !revokedAt.plus(retention).isAfter(now);
    }

    public boolean rejects(Instant issuedAt, Instant now, Duration retention) {
        return !isExpired(now, retention) && issuedAt.getEpochSecond() <= revokedAt.getEpochSecond();
    }

    public AccessTokenRevocationCutoff later(AccessTokenRevocationCutoff other) {
        return other.revokedAt.isAfter(revokedAt) ? other : this;
    }
}
