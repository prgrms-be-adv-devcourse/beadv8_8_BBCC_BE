package com.bbcc.kidly.boundedcontext.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.bbcc.kidly.boundedcontext.member.domain.AccessTokenRevocation;
import com.bbcc.kidly.boundedcontext.member.domain.AccessTokenRevocationRepository;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class AccessTokenRevocationRepositoryIntegrationTest {

    // Cleaner가 테스트 도중 돌아도 지워지지 않도록 지금 시각 기준으로 잡는다. DB 정밀도(마이크로초)에 맞춘다
    private static final LocalDateTime NOW = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);

    @Autowired
    private AccessTokenRevocationRepository repository;

    @Test
    @DisplayName("같은 회원을 다시 무효화하면 더 늦은 시각만 남는다")
    void upsertKeepsLater() {
        repository.upsert(1L, NOW, NOW);
        repository.upsert(1L, NOW.minusMinutes(1), NOW);
        repository.upsert(2L, NOW, NOW);
        repository.upsert(2L, NOW.plusMinutes(1), NOW);

        assertThat(repository.findAllRevokedAfter(NOW.minusHours(1)))
            .extracting(AccessTokenRevocation::getMemberId, AccessTokenRevocation::getRevokedAt)
            .containsExactlyInAnyOrder(tuple(1L, NOW), tuple(2L, NOW.plusMinutes(1)));
    }

    @Test
    @DisplayName("기준 시각 뒤의 기록만 조회하고, 기준 시각 전의 기록만 지운다")
    void findAfterAndDeleteBefore() {
        repository.upsert(1L, NOW.minusMinutes(31), NOW);
        repository.upsert(2L, NOW.minusMinutes(29), NOW);
        var since = NOW.minusMinutes(30);

        assertThat(repository.findAllRevokedAfter(since))
            .extracting(AccessTokenRevocation::getMemberId).containsExactly(2L);
        assertThat(repository.deleteRevokedBefore(since)).isEqualTo(1);
        assertThat(repository.findAllRevokedAfter(NOW.minusHours(1)))
            .extracting(AccessTokenRevocation::getMemberId).containsExactly(2L);
    }
}
