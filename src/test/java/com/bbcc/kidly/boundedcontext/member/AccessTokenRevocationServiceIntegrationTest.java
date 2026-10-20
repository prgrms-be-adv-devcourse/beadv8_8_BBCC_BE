package com.bbcc.kidly.boundedcontext.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.member.app.AccessTokenRevocationService;
import com.bbcc.kidly.boundedcontext.member.domain.AccessTokenRevocation;
import com.bbcc.kidly.boundedcontext.member.domain.AccessTokenRevocationRepository;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class AccessTokenRevocationServiceIntegrationTest {

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private AccessTokenRevocationService revocationService;

    @Autowired
    private AccessTokenRevocationRepository repository;

    private static Instant minutesBefore(LocalDateTime now, long minutes) {
        return now.minusMinutes(minutes).atZone(ZoneId.systemDefault()).toInstant();
    }

    @Test
    @DisplayName("커밋되면 DB에 남기고 캐시에도 반영한다")
    void commit() {
        var now = LocalDateTime.now();

        transactionTemplate.executeWithoutResult(status -> revocationService.revoke(1L, now));

        assertThat(revocationService.isRevoked(1L, minutesBefore(now, 1))).isTrue();
        assertThat(repository.findAllRevokedAfter(now.minusMinutes(1))).hasSize(1);
    }

    @Test
    @DisplayName("롤백되면 DB에도 캐시에도 남지 않는다")
    void rollback() {
        var now = LocalDateTime.now();

        transactionTemplate.executeWithoutResult(status -> {
            revocationService.revoke(1L, now);
            status.setRollbackOnly();
        });

        assertThat(revocationService.isRevoked(1L, minutesBefore(now, 1))).isFalse();
        assertThat(repository.findAllRevokedAfter(now.minusMinutes(1))).isEmpty();
    }

    @Test
    @DisplayName("트랜잭션 밖에서 부르면 예외가 난다")
    void requiresTransaction() {
        assertThatThrownBy(() -> revocationService.revoke(1L, LocalDateTime.now()))
            .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    @DisplayName("재시작하면 DB 기록으로 캐시를 다시 채운다")
    void reload() {
        var now = LocalDateTime.now();
        repository.upsert(1L, now, now);
        assertThat(revocationService.isRevoked(1L, minutesBefore(now, 1))).isFalse();

        revocationService.reload();

        assertThat(revocationService.isRevoked(1L, minutesBefore(now, 1))).isTrue();
    }

    @Test
    @DisplayName("같은 회원을 이른 시각 다음 늦은 시각으로 무효화하면 늦은 기준이 적용된다")
    void laterWinsAfterEarlier() {
        var now = LocalDateTime.now();

        transactionTemplate.executeWithoutResult(status -> revocationService.revoke(1L, now.minusMinutes(10)));
        transactionTemplate.executeWithoutResult(status -> revocationService.revoke(1L, now.minusMinutes(5)));

        assertThat(revocationService.isRevoked(1L, minutesBefore(now, 7))).isTrue();
    }

    @Test
    @DisplayName("같은 회원을 늦은 시각 다음 이른 시각으로 무효화해도 늦은 기준이 남는다")
    void laterWinsBeforeEarlier() {
        var now = LocalDateTime.now();

        transactionTemplate.executeWithoutResult(status -> revocationService.revoke(1L, now.minusMinutes(5)));
        transactionTemplate.executeWithoutResult(status -> revocationService.revoke(1L, now.minusMinutes(10)));

        assertThat(revocationService.isRevoked(1L, minutesBefore(now, 7))).isTrue();
    }

    @Test
    @DisplayName("reload하면 DB에 없는 회원은 캐시에서 빠진다")
    void reloadDropsMissing() {
        var now = LocalDateTime.now();
        transactionTemplate.executeWithoutResult(status -> revocationService.revoke(1L, now));
        assertThat(revocationService.isRevoked(1L, minutesBefore(now, 1))).isTrue();
        repository.deleteRevokedBefore(now.plusMinutes(1));

        revocationService.reload();

        assertThat(revocationService.isRevoked(1L, minutesBefore(now, 1))).isFalse();
    }

    @Test
    @DisplayName("유효 기간 + 시계 오차가 지난 기록만 지운다")
    void deleteExpired() {
        var now = LocalDateTime.now();
        repository.upsert(1L, now.minusMinutes(32), now);
        repository.upsert(2L, now.minusMinutes(29), now);

        revocationService.deleteExpired();

        assertThat(repository.findAllRevokedAfter(now.minusDays(1)))
            .extracting(AccessTokenRevocation::getMemberId)
            .containsExactly(2L);
    }
}
