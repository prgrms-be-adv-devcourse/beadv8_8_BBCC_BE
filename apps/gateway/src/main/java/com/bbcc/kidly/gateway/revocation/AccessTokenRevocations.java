package com.bbcc.kidly.gateway.revocation;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 회원별 Access Token 무효화 시각(메모리). 회원 도메인의 무효화 판정과 같은 규칙이다:
 * 토큰 iat의 초가 무효화 시각의 초와 같거나 이전이면 거절하고, 보존 기간(토큰 유효 기간 + 시계 오차)이 지나면 버린다.
 * Gateway가 한 대라는 전제다. 여러 대가 되면 Redis로 옮긴다(MSA 전환 가이드 5.3절).
 * now를 받는 것은 테스트가 시각을 정하기 위해서다. 부르는 쪽은 Instant.now()를 넘긴다.
 */
public class AccessTokenRevocations {

    private final Map<Long, Instant> revokedAts = new ConcurrentHashMap<>();
    private final Duration retention;

    public AccessTokenRevocations(Duration retention) {
        this.retention = retention;
    }

    // 무효화 기록이 의미 있는 기간. 리스너가 되감을 기간이기도 하다
    public Duration retention() {
        return retention;
    }

    // 같은 회원에 여러 번 오면 가장 늦은 시각을 남긴다(순서가 바뀌어 와도 같다)
    public void revoke(Long memberId, Instant revokedAt, Instant now) {
        revokedAts.merge(memberId, revokedAt, (current, incoming) -> incoming.isAfter(current) ? incoming : current);
        // 보존 기간이 지난 기록을 함께 지운다. 무효화는 드물어 매번 훑어도 된다
        revokedAts.values().removeIf(at -> isExpired(at, now));
    }

    public boolean isRevoked(Long memberId, Instant issuedAt, Instant now) {
        var revokedAt = revokedAts.get(memberId);
        return revokedAt != null && !isExpired(revokedAt, now)
            && issuedAt.getEpochSecond() <= revokedAt.getEpochSecond();
    }

    private boolean isExpired(Instant revokedAt, Instant now) {
        return !revokedAt.plus(retention).isAfter(now);
    }
}
