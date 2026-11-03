package com.bbcc.kidly.boundedcontext.order.app.replica;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.order.app.replica.dto.ChildChangedCommand;
import com.bbcc.kidly.boundedcontext.order.app.replica.dto.ProductChangedCommand;
import com.bbcc.kidly.boundedcontext.order.app.replica.dto.SellerChangedCommand;
import com.bbcc.kidly.boundedcontext.support.AsyncWait;
import com.bbcc.kidly.boundedcontext.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class ReplicaConcurrencyIntegrationTest {

    private static final LocalDateTime BASE = LocalDateTime.of(2026, 11, 2, 10, 0);

    @Autowired
    private ReplicaService service;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    @ParameterizedTest
    @ValueSource(strings = {"product", "child", "seller"})
    @DisplayName("기존 복사본 동시 갱신: 최신 이벤트가 먼저 커밋돼도 과거 값으로 되돌아가지 않는다")
    void existingReplicaKeepsNewest(String kind) throws Exception {
        apply(kind, BASE);
        newerCommitsBeforeOlder(() -> apply(kind, BASE.plusMinutes(2)), () -> apply(kind, BASE.plusMinutes(1)));
        assertNewest(kind);
    }

    @ParameterizedTest
    @ValueSource(strings = {"product", "child", "seller"})
    @DisplayName("최초 복사본 동시 생성: 과거 이벤트는 유일 제약 오류 없이 최신 행에 수렴한다")
    void firstReplicaKeepsNewest(String kind) throws Exception {
        newerCommitsBeforeOlder(() -> apply(kind, BASE.plusMinutes(2)), () -> apply(kind, BASE.plusMinutes(1)));
        assertNewest(kind);
    }

    @Test
    @DisplayName("자녀 삭제와 과거 수정이 겹쳐도 최신 삭제 표시를 유지한다")
    void childDeletionKeepsNewest() throws Exception {
        apply("child", BASE);
        newerCommitsBeforeOlder(() -> service.applyChildDeleted(1L, 1L, BASE.plusMinutes(2)),
            () -> apply("child", BASE.plusMinutes(1)));
        assertThat(jdbcTemplate.queryForObject("select is_deleted from orders.replica_child", Boolean.class)).isTrue();
        assertThat(jdbcTemplate.queryForObject("select event_occurred_at from orders.replica_child",
            LocalDateTime.class)).isEqualTo(BASE.plusMinutes(2));
    }

    private void apply(String kind, LocalDateTime at) {
        switch (kind) {
            case "product" -> service.applyProductChanged(new ProductChangedCommand(1L, 2L, at.toString(), 3L,
                "ON_SALE", List.of(new ProductChangedCommand.Option(10L, "옵션", "110", 1000L, false, "ON_SALE")), at));
            case "child" -> service.applyChildChanged(new ChildChangedCommand(1L, 2L, at.toString(),
                new BigDecimal("110.0"), new BigDecimal("20.0"), "NORMAL", false, at));
            case "seller" -> service.applySellerChanged(new SellerChangedCommand(1L, at.toString(), "ACTIVE", at));
            default -> throw new IllegalArgumentException(kind);
        }
    }

    private void assertNewest(String kind) {
        String nameColumn = switch (kind) {
            case "product" -> "product_name";
            case "child" -> "name";
            case "seller" -> "business_name";
            default -> throw new IllegalArgumentException(kind);
        };
        assertThat(jdbcTemplate.queryForObject("select " + nameColumn + " from orders.replica_" + kind, String.class))
            .isEqualTo(BASE.plusMinutes(2).toString());
        assertThat(jdbcTemplate.queryForObject("select event_occurred_at from orders.replica_" + kind,
            LocalDateTime.class)).isEqualTo(BASE.plusMinutes(2));
        assertThat(jdbcTemplate.queryForObject("select count(*) from orders.replica_" + kind, Long.class)).isEqualTo(1);
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
