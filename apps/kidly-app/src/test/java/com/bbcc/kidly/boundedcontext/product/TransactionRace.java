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

/**
 * 통합 테스트용: 두 작업을 실제 스레드 두 개로 겹쳐 실행해, 뒤의 작업이 앞의 커밋을 기다리는지 본다.
 * 앞의 작업은 바깥 트랜잭션 안에서 실행해 커밋 직전에 멈추고, 그동안 뒤의 작업을 시작한다.
 * 뒤의 작업이 DB 잠금을 기다리는 세션이 생기면 기다린 것으로 보고, 잠금 대기 없이 끝나면 기다리지 않은 것으로 본다.
 * 테스트 클래스패스의 컴포넌트 스캔에 잡혀 모든 통합 · 기능 테스트 컨텍스트에 똑같이 등록된다.
 */
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

    /**
     * 뒤의 작업이 앞의 커밋을 기다렸는지와, 뒤의 작업이 던진 예외(없으면 null)다.
     */
    record Outcome(boolean waited, Throwable error) {
    }

    /**
     * 앞의 작업을 기다린 뒤 업무 예외로 거절됐는지 확인한다.
     */
    static void assertWaitedThenRejected(Outcome outcome, ErrorCode errorCode) {
        assertThat(outcome.waited()).isTrue();
        assertThat(outcome.error()).isInstanceOfSatisfying(BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(errorCode));
    }

    // 앞의 작업을 커밋 직전에 멈춘 채 뒤의 작업을 시작하고, 뒤의 작업이 잠금을 기다리는지 본 뒤 앞을 커밋한다
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

    // 앞의 작업이 멈출 때까지 기다린다. 앞의 작업이 예외로 먼저 끝나면 기다리지 않고 그 예외를 보인다
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

    // 뒤의 작업이 잠금 대기에 들어가면 true, 그 전에 끝나면 false다. 제한 시간 안에 둘 다 아니면 앞을 기다리는 중으로 본다
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
