package com.bbcc.kidly.gateway.revocation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AccessTokenRevocationsTest {

    private static final Duration RETENTION = Duration.ofMinutes(31);
    private static final Instant REVOKED_AT = Instant.parse("2026-10-30T00:00:00.500Z");

    private final AccessTokenRevocations revocations = new AccessTokenRevocations(RETENTION);

    @Test
    @DisplayName("무효화 시각과 같은 초까지 발급된 토큰을 거절한다")
    void rejectsUntilSameSecond() {
        revocations.revoke(1L, REVOKED_AT, REVOKED_AT);

        var now = REVOKED_AT.plusSeconds(10);
        assertThat(revocations.isRevoked(1L, Instant.parse("2026-10-29T23:59:59Z"), now)).isTrue();
        assertThat(revocations.isRevoked(1L, Instant.parse("2026-10-30T00:00:00Z"), now)).isTrue();
        assertThat(revocations.isRevoked(1L, Instant.parse("2026-10-30T00:00:01Z"), now)).isFalse();
        assertThat(revocations.isRevoked(2L, Instant.parse("2026-10-29T23:59:59Z"), now)).isFalse();
    }

    @Test
    @DisplayName("보존 기간이 지나면 거절하지 않는다")
    void expires() {
        revocations.revoke(1L, REVOKED_AT, REVOKED_AT);

        var issuedAt = REVOKED_AT.minusSeconds(1);
        assertThat(revocations.isRevoked(1L, issuedAt, REVOKED_AT.plus(RETENTION).minusMillis(1))).isTrue();
        assertThat(revocations.isRevoked(1L, issuedAt, REVOKED_AT.plus(RETENTION))).isFalse();
    }

    @Test
    @DisplayName("같은 회원에 여러 번 오면 가장 늦은 시각을 남긴다")
    void keepsLatest() {
        var later = REVOKED_AT.plusSeconds(60);
        revocations.revoke(1L, later, later);
        revocations.revoke(1L, REVOKED_AT, later);

        assertThat(revocations.isRevoked(1L, REVOKED_AT.plusSeconds(30), later)).isTrue();
    }
}
