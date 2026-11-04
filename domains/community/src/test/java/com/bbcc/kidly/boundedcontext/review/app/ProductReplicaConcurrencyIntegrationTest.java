package com.bbcc.kidly.boundedcontext.review.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.review.app.port.in.ProductReplicaRecorder.Option;
import com.bbcc.kidly.boundedcontext.review.app.port.in.ProductReplicaRecorder.ProductChanged;
import com.bbcc.kidly.boundedcontext.support.AsyncWait;
import com.bbcc.kidly.boundedcontext.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class ProductReplicaConcurrencyIntegrationTest {

    private static final LocalDateTime BASE = LocalDateTime.of(2026, 11, 2, 10, 0);

    @Autowired
    private ProductReplicaService service;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("기존·신규 상품 동시 반영: 최신 이벤트가 먼저 커밋돼도 최신 값과 시각을 유지한다")
    void concurrentReplicaKeepsNewest(boolean existing) throws Exception {
        if (existing) {
            apply(BASE);
        }
        newerCommitsBeforeOlder(() -> apply(BASE.plusMinutes(2)), () -> apply(BASE.plusMinutes(1)));
        assertThat(jdbcTemplate.queryForObject("select source_occurred_at from community.replica_product",
            LocalDateTime.class)).isEqualTo(BASE.plusMinutes(2));
        assertThat(jdbcTemplate.queryForObject("select name from community.replica_product", String.class))
            .isEqualTo(BASE.plusMinutes(2).toString());
        assertThat(jdbcTemplate.queryForObject("select count(*) from community.replica_product", Long.class))
            .isEqualTo(1);
    }

    private void apply(LocalDateTime at) {
        service.record(new ProductChanged(1L, at.toString(), null, true, List.of(new Option(true, true)), at));
    }

    private void newerCommitsBeforeOlder(Runnable newer, Runnable older) throws Exception {
        CountDownLatch flushed = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger olderPid = new AtomicInteger();
        CompletableFuture<Void> first = CompletableFuture.runAsync(() -> transactionTemplate.executeWithoutResult(s -> {
            newer.run();
            entityManager.flush();
            flushed.countDown();
            await(release);
        }));
        CompletableFuture<Void> second = null;
        try {
            assertThat(flushed.await(10, TimeUnit.SECONDS)).isTrue();
            second = CompletableFuture.runAsync(() -> transactionTemplate.executeWithoutResult(s -> {
                olderPid.set(jdbcTemplate.queryForObject("select pg_backend_pid()", Integer.class));
                older.run();
            }));
            AsyncWait.until(Duration.ofSeconds(10), () -> Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "select exists(select 1 from pg_stat_activity where pid = ? and wait_event_type = 'Lock')",
                Boolean.class, olderPid.get())));
        } finally {
            release.countDown();
            try {
                first.get(15, TimeUnit.SECONDS);
            } finally {
                if (second != null) {
                    second.get(15, TimeUnit.SECONDS);
                }
            }
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(20, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
