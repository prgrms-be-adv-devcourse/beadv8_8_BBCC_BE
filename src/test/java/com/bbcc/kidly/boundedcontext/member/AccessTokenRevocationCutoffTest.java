package com.bbcc.kidly.boundedcontext.member;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.member.domain.AccessTokenRevocationCutoff;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AccessTokenRevocationCutoffTest {

    private static final Duration RETENTION = Duration.ofMinutes(30).plusSeconds(60);

    private final Instant revokedAt = Instant.parse("2026-01-01T00:00:10Z");
    private final AccessTokenRevocationCutoff cutoff = new AccessTokenRevocationCutoff(revokedAt.plusMillis(500));
    private final Instant now = revokedAt.plusSeconds(60);

    @Test
    @DisplayName("기준 시각 1초 전에 발급된 토큰은 거절한다")
    void rejectsEarlier() {
        assertThat(cutoff.rejects(revokedAt.minusSeconds(1), now, RETENTION)).isTrue();
    }

    @Test
    @DisplayName("기준 시각과 같은 초에 발급된 토큰은 밀리초가 더 늦어도 거절한다")
    void rejectsSameSecond() {
        assertThat(cutoff.rejects(revokedAt, now, RETENTION)).isTrue();
        assertThat(cutoff.rejects(revokedAt.plusMillis(900), now, RETENTION)).isTrue();
    }

    @Test
    @DisplayName("기준 시각 1초 뒤에 발급된 토큰은 허용한다")
    void allowsLater() {
        assertThat(cutoff.rejects(revokedAt.plusSeconds(1), now, RETENTION)).isFalse();
    }

    @Test
    @DisplayName("보존 기간(유효 기간 + 시계 오차)이 지나면 거절하지 않는다")
    void retentionBoundary() {
        var expiresAt = cutoff.revokedAt().plus(RETENTION);

        assertThat(cutoff.rejects(revokedAt, expiresAt.minusMillis(1), RETENTION)).isTrue();
        assertThat(cutoff.rejects(revokedAt, expiresAt, RETENTION)).isFalse();
        assertThat(cutoff.isExpired(expiresAt, RETENTION)).isTrue();
    }

    @Test
    @DisplayName("later는 더 늦은 기준을 돌려준다")
    void later() {
        var earlier = new AccessTokenRevocationCutoff(revokedAt);
        var laterOne = new AccessTokenRevocationCutoff(revokedAt.plusSeconds(10));

        assertThat(earlier.later(laterOne)).isEqualTo(laterOne);
        assertThat(laterOne.later(earlier)).isEqualTo(laterOne);
    }

    @Test
    @DisplayName("LocalDateTime은 시스템 시간대로 Instant가 된다")
    void of() {
        var time = LocalDateTime.of(2026, 1, 1, 9, 0);

        assertThat(AccessTokenRevocationCutoff.of(time).revokedAt())
            .isEqualTo(time.atZone(ZoneId.systemDefault()).toInstant());
    }
}
