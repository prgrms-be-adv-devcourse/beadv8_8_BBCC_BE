package com.bbcc.kidly.boundedcontext.product;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.global.error.ErrorCode;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
class TransactionRace {

    private static final long TIMEOUT_SECONDS = 10;
    private static final long POLL_MILLIS = 20;

    private final TransactionTemplate transactionTemplate;
    private final JdbcTemplate jdbcTemplate;

    TransactionRace(PlatformTransactionManager transactionManager, JdbcTemplate jdbcTemplate) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.jdbcTemplate = jdbcTemplate;
    }

    record Outcome(boolean waited, Throwable error) {
    }

    static void assertWaitedThenRejected(Outcome outcome, ErrorCode errorCode) {
        assertThat(outcome.waited()).isTrue();
        assertThat(outcome.error()).isInstanceOfSatisfying(BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(errorCode));
    }

    Outcome run(Runnable first, Runnable second) throws Exception {
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> firstDone = pool.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                first.run();
                held.countDown();
                awaitQuietly(release);
            }));
            awaitHeld(held, firstDone);
            Future<Throwable> secondDone = pool.submit(() -> {
                try {
                    second.run();
                    return null;
                } catch (RuntimeException e) {
                    return e;
                }
            });
            boolean waited = awaitSecondWaitingOrDone(secondDone);
            release.countDown();
            firstDone.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return new Outcome(waited, secondDone.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            pool.shutdownNow();
            pool.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }

    private static void awaitHeld(CountDownLatch held, Future<?> firstDone) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        while (System.nanoTime() < deadline) {
            if (held.await(POLL_MILLIS, TimeUnit.MILLISECONDS)) {
                return;
            }
            if (firstDone.isDone()) {
                firstDone.get();
                throw new IllegalStateException("앞의 작업이 멈추지 않고 끝났습니다");
            }
        }
        throw new IllegalStateException("앞의 작업이 " + TIMEOUT_SECONDS + "초 안에 멈추지 않았습니다");
    }

    private boolean awaitSecondWaitingOrDone(Future<?> secondDone) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        while (System.nanoTime() < deadline) {
            if (secondDone.isDone()) {
                return false;
            }
            if (hasLockWaiter()) {
                return true;
            }
            Thread.sleep(POLL_MILLIS);
        }
        return !secondDone.isDone();
    }

    private boolean hasLockWaiter() {
        Long waiters = jdbcTemplate.queryForObject("select count(*) from pg_stat_activity"
            + " where wait_event_type = 'Lock' and datname = current_database()", Long.class);
        return waiters != null && waiters > 0;
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
