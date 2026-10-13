package com.bbcc.kidly.boundedcontext.member;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.member.app.RefreshTokenCleaner;
import com.bbcc.kidly.boundedcontext.member.domain.RefreshTokenRepository;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class RefreshTokenCleanerIntegrationTest {

    @Autowired
    private RefreshTokenCleaner refreshTokenCleaner;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Test
    @DisplayName("만료된 토큰만 지운다")
    void deleteExpired() {
        var now = LocalDateTime.now();
        refreshTokenRepository.upsert(1L, "expired", now.minusMinutes(1), now.minusDays(14));
        refreshTokenRepository.upsert(2L, "valid", now.plusDays(1), now);

        refreshTokenCleaner.deleteExpired();

        assertThat(refreshTokenRepository.findByTokenHash("expired")).isEmpty();
        assertThat(refreshTokenRepository.findByTokenHash("valid")).isPresent();
    }
}
