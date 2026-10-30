package com.bbcc.kidly.boundedcontext.order.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.order.app.port.SingleServerJobRunner;
import com.bbcc.kidly.boundedcontext.order.support.IntegrationTest;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 주문 스케줄러 작업을 한 번에 한 서버에서만(#564): PostgreSQL advisory xact lock(kidly.order.{작업}). 서버 여러 대 대신
 * 스레드 둘이 각자 연결로 같은 작업을 시도한다.
 */
@IntegrationTest
class AdvisoryLockJobRunnerIntegrationTest {

    @Autowired
    private SingleServerJobRunner runner;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("같은 작업을 동시에 시도하면 하나만 실행되고, 다른 하나는 기다리지 않고 건너뛴다(false)")
    void onlyOneRuns() throws Exception {
        CountDownLatch running = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<Boolean> first = CompletableFuture.supplyAsync(() -> runner.run("test-same", () -> {
            running.countDown();
            await(release);
        }));
        assertThat(running.await(10, TimeUnit.SECONDS)).isTrue();
        AtomicBoolean secondRan = new AtomicBoolean();

        boolean second = CompletableFuture.supplyAsync(() -> runner.run("test-same", () -> secondRan.set(true)))
            .get(5, TimeUnit.SECONDS);

        assertThat(second).isFalse();
        assertThat(secondRan).isFalse();
        release.countDown();
        assertThat(first.get(10, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    @DisplayName("작업이 끝나면(정상 · 예외 — 예외는 그대로 던짐) DB에 잠금이 남지 않고, 다른 스레드(다른 연결)가 다시 잡는다")
    void releasedAfterFinishOrException() throws Exception {
        assertThat(runner.run("test-release", () -> { })).isTrue();
        assertNoAdvisoryLockLeft();
        assertThat(onOtherThread("test-release")).isTrue();

        assertThatThrownBy(() -> runner.run("test-release", () -> {
            throw new IllegalStateException("작업 실패");
        })).isInstanceOf(IllegalStateException.class).hasMessage("작업 실패");
        assertNoAdvisoryLockLeft();
        assertThat(onOtherThread("test-release")).isTrue();
    }

    // 트랜잭션 잠금이라 끝나면 풀린다. 세션 잠금이었다면 연결이 풀로 돌아가도 남아(pg_locks) 다른 서버가 영영 못 잡는다
    private void assertNoAdvisoryLockLeft() {
        assertThat(jdbcTemplate.queryForObject("select count(*) from pg_locks where locktype = 'advisory'",
            Long.class)).isZero();
    }

    private boolean onOtherThread(String jobName) throws Exception {
        return CompletableFuture.supplyAsync(() -> runner.run(jobName, () -> { })).get(5, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("작업 이름이 다르면 서로 막지 않는다")
    void differentNamesDoNotBlock() throws Exception {
        CountDownLatch running = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<Boolean> holder = CompletableFuture.supplyAsync(() -> runner.run("test-one", () -> {
            running.countDown();
            await(release);
        }));
        assertThat(running.await(10, TimeUnit.SECONDS)).isTrue();

        boolean other = CompletableFuture.supplyAsync(() -> runner.run("test-two", () -> { }))
            .get(5, TimeUnit.SECONDS);

        assertThat(other).isTrue();
        release.countDown();
        assertThat(holder.get(10, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    @DisplayName("작업은 트랜잭션 밖에서 돈다: 대상 하나하나의 처리 트랜잭션이 지금처럼 따로 커밋된다(잠금 트랜잭션에 합류하지 않음)")
    void jobRunsWithoutTransaction() {
        AtomicBoolean inTransaction = new AtomicBoolean(true);

        runner.run("test-tx", () -> inTransaction.set(TransactionSynchronizationManager.isActualTransactionActive()));

        assertThat(inTransaction).isFalse();
    }

    @Test
    @DisplayName("잠금 키는 kidly.order.{작업}: 아웃박스 릴레이는 전과 같은 키라 배포 중 옛 서버의 릴레이와도 서로 막는다")
    void lockKey() throws Exception {
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<Void> oldServer = CompletableFuture.runAsync(() -> transactionTemplate.executeWithoutResult(
            status -> {
                jdbcTemplate.queryForObject("select pg_try_advisory_xact_lock(hashtext('kidly.order.outbox-relay'))",
                    Boolean.class);
                locked.countDown();
                await(release);
            }));
        assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

        boolean ran = CompletableFuture.supplyAsync(() -> runner.run("outbox-relay", () -> { }))
            .get(5, TimeUnit.SECONDS);

        assertThat(ran).isFalse();
        release.countDown();
        oldServer.get(10, TimeUnit.SECONDS);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
