package com.bbcc.kidly.gateway.revocation;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.gateway.support.IntegrationTest;
import com.bbcc.kidly.shared.auth.PrincipalId;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 무효화 목록을 실제 Redis에 쓰고 읽는다. 보존 기간은 설정(토큰 유효 기간 30분 + 시계 오차 60초)에서 나온 31분이다.
 */
@IntegrationTest
class AccessTokenRevocationsIntegrationTest {

    private static final Duration RETENTION = Duration.ofMinutes(31);
    private static final PrincipalId MEMBER_1 = PrincipalId.member(1L);
    private static final PrincipalId MEMBER_2 = PrincipalId.member(2L);
    private static final PrincipalId ADMIN_1 = PrincipalId.admin(1L);
    private static final Instant REVOKED_AT = Instant.parse("2026-10-30T00:00:00.500Z");

    @Autowired
    private AccessTokenRevocations revocations;

    @Autowired
    private StringRedisTemplate redis;

    @Test
    @DisplayName("무효화 시각과 같은 초까지 발급된 토큰을 거절한다. 다른 주체는 거절하지 않는다")
    void rejectsUntilSameSecond() {
        revocations.revoke(MEMBER_1, REVOKED_AT, REVOKED_AT);

        var now = REVOKED_AT.plusSeconds(10);
        assertThat(isRevoked(MEMBER_1, Instant.parse("2026-10-29T23:59:59Z"), now)).isTrue();
        assertThat(isRevoked(MEMBER_1, Instant.parse("2026-10-30T00:00:00Z"), now)).isTrue();
        assertThat(isRevoked(MEMBER_1, Instant.parse("2026-10-30T00:00:01Z"), now)).isFalse();
        assertThat(isRevoked(MEMBER_2, Instant.parse("2026-10-29T23:59:59Z"), now)).isFalse();
        // 회원 1과 관리자 1은 다른 주체다
        assertThat(isRevoked(ADMIN_1, Instant.parse("2026-10-29T23:59:59Z"), now)).isFalse();
    }

    @Test
    @DisplayName("보존 기간이 지나면 거절하지 않는다")
    void expires() {
        var revokedAt = Instant.parse("2026-10-30T00:00:00Z");
        revocations.revoke(MEMBER_1, revokedAt, revokedAt);

        var issuedAt = revokedAt.minusSeconds(1);
        assertThat(isRevoked(MEMBER_1, issuedAt, revokedAt.plus(RETENTION).minusMillis(1))).isTrue();
        assertThat(isRevoked(MEMBER_1, issuedAt, revokedAt.plus(RETENTION))).isFalse();
    }

    @Test
    @DisplayName("보존 기간이 지난 기록은 다음 무효화 때 지운다(Redis에 쌓이지 않는다)")
    void removesExpiredOnRevoke() {
        revocations.revoke(MEMBER_1, REVOKED_AT, REVOKED_AT);

        var later = REVOKED_AT.plus(RETENTION).plusSeconds(1);
        revocations.revoke(MEMBER_2, later, later);

        assertThat(redis.opsForHash().keys("kidly:gateway:revocations")).containsExactly(MEMBER_2.toSubject());
    }

    @Test
    @DisplayName("같은 주체에 여러 번 오면 순서가 바뀌어도 가장 늦은 시각을 남긴다")
    void keepsLatest() {
        var later = REVOKED_AT.plusSeconds(60);
        revocations.revoke(MEMBER_1, later, later);
        revocations.revoke(MEMBER_1, REVOKED_AT, later);

        assertThat(isRevoked(MEMBER_1, REVOKED_AT.plusSeconds(30), later)).isTrue();
        assertThat(isRevoked(MEMBER_1, later.plusSeconds(1), later)).isFalse();
    }

    @Test
    @DisplayName("같은 무효화를 두 번 받아도 한 번 받은 것과 같다")
    void sameRevocationTwiceIsOnce() {
        revocations.revoke(MEMBER_1, REVOKED_AT, REVOKED_AT);
        revocations.revoke(MEMBER_1, REVOKED_AT, REVOKED_AT);

        var now = REVOKED_AT.plusSeconds(10);
        assertThat(isRevoked(MEMBER_1, Instant.parse("2026-10-30T00:00:00Z"), now)).isTrue();
        assertThat(isRevoked(MEMBER_1, Instant.parse("2026-10-30T00:00:01Z"), now)).isFalse();
    }

    private boolean isRevoked(PrincipalId principal, Instant issuedAt, Instant now) {
        return revocations.isRevoked(principal, issuedAt, now).block();
    }
}
