package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.app.port.ai.AiCallQuota;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * AI 호출 횟수 상한이 실제 PostgreSQL에서 하루 상한까지만 호출을 허용하는지 확인한다.
 */
@IntegrationTest
class AiCallQuotaIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);
    private static final long WAIT_SECONDS = 20;

    @Autowired
    private AiCallQuota quota;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("상한까지는 허용하고 상한에 닿으면 거절하며 횟수는 상한에서 멈춘다")
    void allowUpToLimitThenRefuse() {
        List<Boolean> results = List.of(
            quota.tryAcquire(TODAY, 3),
            quota.tryAcquire(TODAY, 3),
            quota.tryAcquire(TODAY, 3),
            quota.tryAcquire(TODAY, 3));

        assertThat(results).containsExactly(true, true, true, false);
        assertThat(callCount(TODAY)).isEqualTo(3);
    }

    @Test
    @DisplayName("날짜가 다르면 날짜마다 따로 센다")
    void countEachDateSeparately() {
        LocalDate tomorrow = TODAY.plusDays(1);

        boolean today = quota.tryAcquire(TODAY, 1);
        boolean todayAgain = quota.tryAcquire(TODAY, 1);
        boolean nextDay = quota.tryAcquire(tomorrow, 1);

        assertThat(today).isTrue();
        assertThat(todayAgain).isFalse();
        assertThat(nextDay).isTrue();
        assertThat(callCount(TODAY)).isEqualTo(1);
        assertThat(callCount(tomorrow)).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    @DisplayName("상한이 0 이하면 거절하고 횟수 줄을 만들지 않는다")
    void refuseWithoutCountingWhenLimitIsNotPositive(int dailyLimit) {
        boolean acquired = quota.tryAcquire(TODAY, dailyLimit);

        assertThat(acquired).isFalse();
        assertThat(callCount(TODAY)).isNull();
    }

    @Test
    @DisplayName("스레드 둘이 동시에 세어도 허용한 횟수가 상한을 넘지 않는다")
    void doNotExceedLimitWhenCountedConcurrently() throws Exception {
        int dailyLimit = 50;
        int attemptsPerThread = 100;
        AtomicInteger allowed = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> first = executor.submit(
                () -> acquireRepeatedly(dailyLimit, attemptsPerThread, ready, start, allowed));
            Future<?> second = executor.submit(
                () -> acquireRepeatedly(dailyLimit, attemptsPerThread, ready, start, allowed));
            assertThat(ready.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            waitFor(first);
            waitFor(second);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }

        assertThat(allowed.get()).isEqualTo(dailyLimit);
        assertThat(callCount(TODAY)).isEqualTo(dailyLimit);
    }

    private void acquireRepeatedly(int dailyLimit, int attempts, CountDownLatch ready, CountDownLatch start,
        AtomicInteger allowed) {
        ready.countDown();
        awaitLatch(start);
        for (int i = 0; i < attempts; i++) {
            if (quota.tryAcquire(TODAY, dailyLimit)) {
                allowed.incrementAndGet();
            }
        }
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

    private static void waitFor(Future<?> future) {
        try {
            future.get(WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException(e);
        }
    }

    private Integer callCount(LocalDate date) {
        List<Integer> counts = jdbcTemplate.queryForList(
            "select call_count from recommend.ai_call_usage where usage_date = ?", Integer.class, date);
        if (counts.isEmpty()) {
            return null;
        }
        return counts.get(0);
    }
}
