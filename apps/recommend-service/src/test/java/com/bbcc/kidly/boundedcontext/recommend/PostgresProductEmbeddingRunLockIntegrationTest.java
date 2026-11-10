package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.recommend.app.port.embedding.ProductEmbeddingRunLock;
import com.bbcc.kidly.support.IntegrationTest;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 상품 임베딩 실행 잠금이 실제 PostgreSQL에서 스레드 둘 중 하나만 일을 하게 하는지 확인한다.
 */
@IntegrationTest
class PostgresProductEmbeddingRunLockIntegrationTest {

    private static final long WAIT_SECONDS = 10;

    @Autowired
    private ProductEmbeddingRunLock runLock;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("한쪽이 잠금을 쥐고 있는 동안 다른 쪽은 작업을 실행하지 않고 false를 돌려준다")
    void skipWorkWhileOtherThreadHoldsLock() throws Exception {
        CountDownLatch holding = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean otherWorkRan = new AtomicBoolean();
        ExecutorService holder = Executors.newSingleThreadExecutor();

        try {
            Future<Boolean> holderRan = holder.submit(() -> runLock.runExclusively(() -> {
                holding.countDown();
                awaitLatch(release);
            }));
            assertThat(holding.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();

            boolean otherRan = runLock.runExclusively(() -> otherWorkRan.set(true));

            assertThat(otherRan).isFalse();
            assertThat(otherWorkRan).isFalse();
            release.countDown();
            assertThat(waitFor(holderRan)).isTrue();
        } finally {
            release.countDown();
            holder.shutdownNow();
        }
    }

    @Test
    @DisplayName("잠금을 쥔 쪽이 끝나면 잠금이 풀려 다른 쪽이 다시 잡아 일을 할 수 있다")
    void acquireAgainAfterHolderFinishes() throws Exception {
        AtomicBoolean secondWorkRan = new AtomicBoolean();
        ExecutorService holder = Executors.newSingleThreadExecutor();

        try {
            assertThat(waitFor(holder.submit(() -> runLock.runExclusively(() -> { })))).isTrue();

            boolean secondRan = runLock.runExclusively(() -> secondWorkRan.set(true));

            assertThat(secondRan).isTrue();
            assertThat(secondWorkRan).isTrue();
            assertThat(advisoryLockCount()).isZero();
        } finally {
            holder.shutdownNow();
        }
    }

    @Test
    @DisplayName("작업이 예외를 던져도 잠금이 풀리고 예외는 그대로 나간다")
    void releaseLockAndRethrowWhenWorkFails() {
        assertThatThrownBy(() -> runLock.runExclusively(() -> {
            throw new IllegalStateException("작업 실패");
        })).isInstanceOf(IllegalStateException.class).hasMessage("작업 실패");

        assertThat(advisoryLockCount()).isZero();
    }

    // 세션 잠금은 같은 세션에서 다시 잡아도 성공하므로, 풀렸는지는 DB가 들고 있는 잠금 수로 확인한다
    private int advisoryLockCount() {
        return jdbcTemplate.queryForObject("select count(*) from pg_locks where locktype = 'advisory'", Integer.class);
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            if (!latch.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("기다리던 신호가 오지 않았습니다");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static <T> T waitFor(Future<T> future) {
        try {
            return future.get(WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException(e);
        }
    }
}
