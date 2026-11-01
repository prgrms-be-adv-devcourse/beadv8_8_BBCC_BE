package com.bbcc.kidly.boundedcontext.delivery.out.lock;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.delivery.app.port.DeliveryJobRunner;
import com.bbcc.kidly.boundedcontext.support.IntegrationTest;
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
 * 배송 작업(택배 조회, #650)을 한 번에 한 서버에서만: PostgreSQL advisory xact lock(kidly.delivery.{작업}). 서버 여러 대 대신
 * 스레드 둘이 각자 연결로 같은 작업을 시도한다.
 */
@IntegrationTest
class DeliveryAdvisoryLockJobRunnerIntegrationTest {

    @Autowired
    private DeliveryJobRunner runner;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("같은 작업을 동시에 시도하면 하나만 실행되고, 다른 하나는 기다리지 않고 건너뛴다(false). 끝나면 잠금이 남지 않는다")
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
        assertThat(jdbcTemplate.queryForObject("select count(*) from pg_locks where locktype = 'advisory'",
            Long.class)).isZero();
    }

    @Test
    @DisplayName("작업은 트랜잭션 밖에서 돈다: 외부 호출이 DB 트랜잭션 · 잠금 트랜잭션 안에서 일어나지 않는다")
    void jobRunsWithoutTransaction() {
        AtomicBoolean inTransaction = new AtomicBoolean(true);

        runner.run("test-tx", () -> inTransaction.set(TransactionSynchronizationManager.isActualTransactionActive()));

        assertThat(inTransaction).isFalse();
    }

    @Test
    @DisplayName("잠금 키는 kidly.delivery.{작업}: 다른 서버가 kidly.delivery.delivery-tracking을 잡고 있으면 건너뛴다")
    void lockKey() throws Exception {
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<Void> otherServer = CompletableFuture.runAsync(() -> transactionTemplate.executeWithoutResult(
            status -> {
                jdbcTemplate.queryForObject(
                    "select pg_try_advisory_xact_lock(hashtext('kidly.delivery.delivery-tracking'))", Boolean.class);
                locked.countDown();
                await(release);
            }));
        assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

        boolean ran = CompletableFuture.supplyAsync(() -> runner.run("delivery-tracking", () -> { }))
            .get(5, TimeUnit.SECONDS);

        assertThat(ran).isFalse();
        release.countDown();
        otherServer.get(10, TimeUnit.SECONDS);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
