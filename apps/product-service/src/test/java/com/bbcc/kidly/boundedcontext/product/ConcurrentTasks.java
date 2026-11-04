package com.bbcc.kidly.boundedcontext.product;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 재고 차감 동시성 테스트용: 작업을 실제 스레드 여러 개에서 함께 출발시키고, 모든 작업이 끝날 때까지 기다린다.
 * 모든 스레드가 준비된 뒤 출발 신호 하나로 함께 시작한다.
 */
final class ConcurrentTasks {

    private ConcurrentTasks() {
    }

    // 작업마다 스레드를 하나씩 두고, 모두 준비되면 출발 신호로 함께 시작한다. 어느 작업이든 예외가 나면 테스트가 실패한다
    static void runConcurrently(List<Runnable> tasks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        try {
            awaitAll(startTogether(executor, tasks));
        } finally {
            executor.shutdown();
        }
    }

    // 테스트가 잠금을 쥔 채 작업을 모두 출발시키고, 모든 작업이 그 잠금을 기다리는 것을 확인한 뒤 잠금을 푼다.
    // 그래서 작업들이 기존 기록 확인을 모두 지난 다음에 겹치는 경쟁이 매번 생긴다
    static void runRacing(InventoryDeductionDb db, InventoryDeductionDb.HeldLock lock, List<Runnable> tasks)
        throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        try {
            List<Future<?>> results;
            try (lock) {
                results = startTogether(executor, tasks);
                db.awaitLockWaiters(tasks.size());
            }
            awaitAll(results);
        } finally {
            executor.shutdown();
        }
    }

    // 모든 작업이 스레드에서 준비된 뒤 출발 신호 하나로 함께 시작시키고, 결과를 기다릴 Future를 돌려준다
    static List<Future<?>> startTogether(ExecutorService executor, List<Runnable> tasks)
        throws InterruptedException {
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> results = new ArrayList<>();
        for (Runnable task : tasks) {
            results.add(executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("출발 신호를 받지 못했습니다");
                }
                task.run();
                return null;
            }));
        }
        assertThat(ready.await(10, TimeUnit.SECONDS)).as("모든 스레드가 준비됨").isTrue();
        start.countDown();
        return results;
    }

    // 모든 작업이 끝날 때까지 기다린 뒤 처음 난 예외를 던진다. 먼저 실패한 작업 때문에 남은 작업을 끊지 않아,
    // 테스트가 끝난 뒤 다른 스레드가 DB를 건드리지 않는다
    static void awaitAll(List<Future<?>> results) throws Exception {
        Exception first = null;
        for (Future<?> result : results) {
            try {
                result.get(60, TimeUnit.SECONDS);
            } catch (ExecutionException | TimeoutException e) {
                if (first == null) {
                    first = e;
                }
            }
        }
        if (first != null) {
            throw first;
        }
    }
}
