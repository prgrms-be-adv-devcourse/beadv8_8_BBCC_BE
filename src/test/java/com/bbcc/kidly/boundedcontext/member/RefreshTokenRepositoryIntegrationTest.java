package com.bbcc.kidly.boundedcontext.member;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.member.domain.RefreshTokenRepository;
import com.bbcc.kidly.boundedcontext.member.out.JpaRefreshTokenRepository;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class RefreshTokenRepositoryIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 13, 9, 0);
    private static final LocalDateTime EXPIRES_AT = NOW.plusDays(14);

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private JpaRefreshTokenRepository jpaRefreshTokenRepository;

    @Test
    @DisplayName("회원의 토큰이 없으면 만들고, 있으면 새 해시로 바꾼다")
    void upsert() {
        refreshTokenRepository.upsert(1L, "hash-1", EXPIRES_AT, NOW);
        refreshTokenRepository.upsert(1L, "hash-2", EXPIRES_AT, NOW);

        assertThat(jpaRefreshTokenRepository.count()).isEqualTo(1);
        assertThat(refreshTokenRepository.findByTokenHash("hash-1")).isEmpty();
        assertThat(refreshTokenRepository.findByTokenHash("hash-2"))
            .hasValueSatisfying(token -> assertThat(token.getMemberId()).isEqualTo(1L));
    }

    @Test
    @DisplayName("저장된 해시가 이전 해시와 같을 때만 바꾼다")
    void rotateOnlyWhenHashMatches() {
        refreshTokenRepository.upsert(1L, "hash-1", EXPIRES_AT, NOW);

        assertThat(refreshTokenRepository.rotate(1L, "other-hash", "hash-2", EXPIRES_AT, NOW)).isZero();
        assertThat(refreshTokenRepository.rotate(1L, "hash-1", "hash-2", EXPIRES_AT, NOW)).isEqualTo(1);
        assertThat(refreshTokenRepository.findByTokenHash("hash-2")).isPresent();
    }

    @Test
    @DisplayName("해시로 토큰을 지운다")
    void deleteByTokenHash() {
        refreshTokenRepository.upsert(1L, "hash-1", EXPIRES_AT, NOW);

        refreshTokenRepository.deleteByTokenHash("hash-1");

        assertThat(jpaRefreshTokenRepository.count()).isZero();
    }

    @Test
    @DisplayName("만료된 토큰만 지운다")
    void deleteExpired() {
        refreshTokenRepository.upsert(1L, "expired", NOW.minusSeconds(1), NOW.minusDays(14));
        refreshTokenRepository.upsert(2L, "valid", NOW.plusSeconds(1), NOW);

        assertThat(refreshTokenRepository.deleteExpired(NOW)).isEqualTo(1);
        assertThat(refreshTokenRepository.findByTokenHash("valid")).isPresent();
    }
}
