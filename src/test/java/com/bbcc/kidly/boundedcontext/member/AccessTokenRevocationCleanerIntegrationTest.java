package com.bbcc.kidly.boundedcontext.member;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.member.app.AccessTokenRevocationCleaner;
import com.bbcc.kidly.boundedcontext.member.domain.AccessTokenRevocation;
import com.bbcc.kidly.boundedcontext.member.domain.AccessTokenRevocationRepository;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class AccessTokenRevocationCleanerIntegrationTest {

    @Autowired
    private AccessTokenRevocationCleaner cleaner;

    @Autowired
    private AccessTokenRevocationRepository repository;

    @Test
    @DisplayName("유효 기간 + 시계 오차가 지난 기록만 지운다")
    void deleteExpired() {
        var now = LocalDateTime.now();
        repository.upsert(1L, now.minusMinutes(32), now);
        repository.upsert(2L, now.minusMinutes(29), now);

        cleaner.deleteExpired();

        // 메모리 정리는 isRevoked 동작으로 드러나지 않는다(만료된 기록은 어차피 거절하지 않음). DB 삭제만 확인한다

        assertThat(repository.findAllRevokedAfter(now.minusDays(1)))
            .extracting(AccessTokenRevocation::getMemberId)
            .containsExactly(2L);
    }
}
