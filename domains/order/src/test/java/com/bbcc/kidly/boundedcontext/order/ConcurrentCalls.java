package com.bbcc.kidly.boundedcontext.order;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import org.hibernate.StaleStateException;
import org.springframework.dao.OptimisticLockingFailureException;

/**
 * 동시 실행 테스트용: 작업들을 출발 신호 하나로 함께 시작하고, 각 작업의 예외를 낙관적 락 충돌과 그 밖으로 나눈다.
 * 작업마다 자기 스레드 · 자기 트랜잭션으로 커밋한다.
 */
public final class ConcurrentCalls {

    private static final long TIMEOUT_SECONDS = 30;

    private ConcurrentCalls() {
    }

    /**
     * @param conflicts 진 작업 수(정상): 낙관적 락, 그리고 호출한 쪽이 따로 인정한 예외
     * @param unexpected 그 밖의 예외(테스트 실패 대상)
     */
    public record Outcome(int conflicts, List<Throwable> unexpected) {
    }

    public static Outcome run(List<Runnable> tasks) throws InterruptedException {
        return run(tasks, error -> false);
    }

    /**
     * @param alsoLost 낙관적 락 외에 "진 쪽 정상"으로 볼 예외(시나리오마다 이유를 주석으로 남긴다)
     */
    public static Outcome run(List<Runnable> tasks, Predicate<Throwable> alsoLost) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        int conflicts = 0;
        List<Throwable> unexpected = new ArrayList<>();
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (Runnable task : tasks) {
                futures.add(executor.submit(() -> {
                    start.await();
                    task.run();
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                try {
                    future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                } catch (ExecutionException e) {
                    if (isOptimisticLockConflict(e.getCause()) || alsoLost.test(e.getCause())) {
                        conflicts++;
                    } else {
                        unexpected.add(e.getCause());
                    }
                } catch (TimeoutException e) {
                    unexpected.add(e);
                }
            }
        } finally {
            executor.shutdown();
            if (!executor.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        }
        return new Outcome(conflicts, unexpected);
    }

    private static boolean isOptimisticLockConflict(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof OptimisticLockingFailureException
                || cause instanceof jakarta.persistence.OptimisticLockException
                || cause instanceof StaleStateException) {
                return true;
            }
        }
        return false;
    }
}
