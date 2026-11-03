package com.bbcc.kidly.boundedcontext.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.auth.domain.RefreshTokenRepository;
import com.bbcc.kidly.boundedcontext.auth.out.JpaRefreshTokenRepository;
import com.bbcc.kidly.shared.auth.PrincipalId;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class RefreshTokenRepositoryIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 13, 9, 0);
    private static final LocalDateTime EXPIRES_AT = NOW.plusDays(14);
    private static final PrincipalId MEMBER_1 = PrincipalId.member(1L);
    private static final PrincipalId MEMBER_2 = PrincipalId.member(2L);
    private static final PrincipalId ADMIN_1 = PrincipalId.admin(1L);

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private JpaRefreshTokenRepository jpaRefreshTokenRepository;

    @Test
    @DisplayName("주체의 토큰이 없으면 만들고, 있으면 새 해시로 바꾼다")
    void upsert() {
        refreshTokenRepository.upsert(MEMBER_1, "hash-1", EXPIRES_AT, NOW);
        refreshTokenRepository.upsert(MEMBER_1, "hash-2", EXPIRES_AT, NOW);

        assertThat(jpaRefreshTokenRepository.count()).isEqualTo(1);
        assertThat(refreshTokenRepository.findByTokenHash("hash-1")).isEmpty();
        assertThat(refreshTokenRepository.findByTokenHash("hash-2"))
            .hasValueSatisfying(token -> assertThat(token.principal()).isEqualTo(MEMBER_1));
    }

    @Test
    @DisplayName("같은 숫자 ID라도 회원과 관리자의 토큰은 다른 행이다")
    void memberAndAdminWithSameIdCoexist() {
        refreshTokenRepository.upsert(MEMBER_1, "member-hash", EXPIRES_AT, NOW);
        refreshTokenRepository.upsert(ADMIN_1, "admin-hash", EXPIRES_AT, NOW);
        refreshTokenRepository.upsert(ADMIN_1, "admin-hash-2", EXPIRES_AT, NOW);

        assertThat(jpaRefreshTokenRepository.count()).isEqualTo(2);
        assertThat(refreshTokenRepository.findByTokenHash("member-hash"))
            .hasValueSatisfying(token -> assertThat(token.principal()).isEqualTo(MEMBER_1));
        assertThat(refreshTokenRepository.findByTokenHash("admin-hash-2"))
            .hasValueSatisfying(token -> assertThat(token.principal()).isEqualTo(ADMIN_1));
    }

    @Test
    @DisplayName("저장된 해시가 이전 해시와 같을 때만 바꾼다")
    void rotateOnlyWhenHashMatches() {
        refreshTokenRepository.upsert(MEMBER_1, "hash-1", EXPIRES_AT, NOW);

        assertThat(refreshTokenRepository.rotate(MEMBER_1, "other-hash", "hash-2", EXPIRES_AT, NOW)).isZero();
        assertThat(refreshTokenRepository.rotate(MEMBER_1, "hash-1", "hash-2", EXPIRES_AT, NOW)).isEqualTo(1);
        assertThat(refreshTokenRepository.findByTokenHash("hash-2")).isPresent();
    }

    @Test
    @DisplayName("주체 종류가 다르면 같은 숫자 ID라도 바꾸지 않는다")
    void rotateOnlyForSamePrincipalType() {
        refreshTokenRepository.upsert(MEMBER_1, "hash-1", EXPIRES_AT, NOW);

        assertThat(refreshTokenRepository.rotate(ADMIN_1, "hash-1", "hash-2", EXPIRES_AT, NOW)).isZero();
        assertThat(refreshTokenRepository.findByTokenHash("hash-1")).isPresent();
    }

    @Test
    @DisplayName("교체하면 직전 해시와 교체 시각을 남긴다")
    void rotateKeepsPreviousHash() {
        refreshTokenRepository.upsert(MEMBER_1, "hash-1", EXPIRES_AT, NOW);

        refreshTokenRepository.rotate(MEMBER_1, "hash-1", "hash-2", EXPIRES_AT, NOW.plusMinutes(1));

        assertThat(refreshTokenRepository.findByTokenHash("hash-2")).hasValueSatisfying(token -> {
            assertThat(token.getPreviousTokenHash()).isEqualTo("hash-1");
            assertThat(token.getRotatedAt()).isEqualTo(NOW.plusMinutes(1));
        });
    }

    @Test
    @DisplayName("다시 로그인하면 직전 해시와 교체 시각을 비운다")
    void upsertClearsPreviousHash() {
        refreshTokenRepository.upsert(MEMBER_1, "hash-1", EXPIRES_AT, NOW);
        refreshTokenRepository.rotate(MEMBER_1, "hash-1", "hash-2", EXPIRES_AT, NOW);

        refreshTokenRepository.upsert(MEMBER_1, "hash-3", EXPIRES_AT, NOW);

        assertThat(refreshTokenRepository.findByTokenHash("hash-3")).hasValueSatisfying(token -> {
            assertThat(token.getPreviousTokenHash()).isNull();
            assertThat(token.getRotatedAt()).isNull();
        });
    }

    @Test
    @DisplayName("직전 해시로 토큰을 찾는다")
    void findByPreviousTokenHash() {
        refreshTokenRepository.upsert(MEMBER_1, "hash-1", EXPIRES_AT, NOW);
        refreshTokenRepository.rotate(MEMBER_1, "hash-1", "hash-2", EXPIRES_AT, NOW);

        assertThat(refreshTokenRepository.findByPreviousTokenHash("hash-1"))
            .hasValueSatisfying(token -> assertThat(token.getTokenHash()).isEqualTo("hash-2"));
        assertThat(refreshTokenRepository.findByPreviousTokenHash("hash-2")).isEmpty();
    }

    @Test
    @DisplayName("해시로 토큰을 지운다")
    void deleteByTokenHash() {
        refreshTokenRepository.upsert(MEMBER_1, "hash-1", EXPIRES_AT, NOW);

        refreshTokenRepository.deleteByTokenHash("hash-1");

        assertThat(jpaRefreshTokenRepository.count()).isZero();
    }

    @Test
    @DisplayName("주체의 토큰만 지운다. 같은 숫자 ID의 다른 종류 주체는 남는다")
    void deleteByPrincipal() {
        refreshTokenRepository.upsert(MEMBER_1, "hash-1", EXPIRES_AT, NOW);
        refreshTokenRepository.upsert(MEMBER_2, "hash-2", EXPIRES_AT, NOW);
        refreshTokenRepository.upsert(ADMIN_1, "hash-3", EXPIRES_AT, NOW);

        refreshTokenRepository.deleteByPrincipal(MEMBER_1);

        assertThat(jpaRefreshTokenRepository.count()).isEqualTo(2);
        assertThat(refreshTokenRepository.findByTokenHash("hash-2")).isPresent();
        assertThat(refreshTokenRepository.findByTokenHash("hash-3")).isPresent();
    }

    @Test
    @DisplayName("만료된 토큰만 지운다")
    void deleteExpired() {
        refreshTokenRepository.upsert(MEMBER_1, "expired", NOW.minusSeconds(1), NOW.minusDays(14));
        refreshTokenRepository.upsert(MEMBER_2, "valid", NOW.plusSeconds(1), NOW);

        assertThat(refreshTokenRepository.deleteExpired(NOW)).isEqualTo(1);
        assertThat(refreshTokenRepository.findByTokenHash("valid")).isPresent();
    }
}
