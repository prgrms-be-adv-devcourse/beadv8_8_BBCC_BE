package com.bbcc.kidly.boundedcontext.order;

import static org.assertj.core.api.Assertions.fail;

import java.time.Duration;
import java.util.concurrent.Delayed;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * 쿼리 수 테스트용(#416): 측정 전에 스케줄러가 쉬는 상태가 될 때까지 기다린다.
 * Hibernate 통계는 모든 스레드의 문장을 세는데, fixedDelay 스케줄러는 컨텍스트가 뜨자마자 첫 실행을 하므로
 * 그 쿼리가 측정 구간에 섞일 수 있다. 쉬는 상태 = 실행 중인 작업 없음 + 큐에 지금 실행할 차례인 작업 없음
 * (단일 스레드라 실행 중 0이어도 첫 실행들이 큐에서 차례를 기다릴 수 있어 큐까지 본다). 고정 sleep 대신 상태를 다시 본다.
 */
public final class SchedulerIdle {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);
    private static final long POLL_MILLIS = 50;

    private SchedulerIdle() {
    }

    public static void await(ThreadPoolTaskScheduler scheduler) {
        await(scheduler, DEFAULT_TIMEOUT);
    }

    /**
     * @throws AssertionError 제한 시간 안에 쉬는 상태가 되지 않으면(실행 중 · 실행할 차례 · 완료 수를 메시지에 담음)
     */
    public static void await(ThreadPoolTaskScheduler scheduler, Duration timeout) {
        ScheduledThreadPoolExecutor executor = scheduler.getScheduledThreadPoolExecutor();
        long deadline = System.nanoTime() + timeout.toNanos();
        while (!isIdle(executor)) {
            if (System.nanoTime() > deadline) {
                fail("스케줄러가 %dms 안에 쉬지 않음: 실행 중 %d, 실행할 차례 %d, 완료 %d",
                    timeout.toMillis(), executor.getActiveCount(), dueCount(executor),
                    executor.getCompletedTaskCount());
            }
            try {
                Thread.sleep(POLL_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("스케줄러를 기다리다 중단됨");
            }
        }
    }

    private static boolean isIdle(ScheduledThreadPoolExecutor executor) {
        return executor.getActiveCount() == 0 && dueCount(executor) == 0;
    }

    private static long dueCount(ScheduledThreadPoolExecutor executor) {
        return executor.getQueue().stream()
            .filter(task -> task instanceof Delayed delayed && delayed.getDelay(TimeUnit.NANOSECONDS) <= 0)
            .count();
    }
}
