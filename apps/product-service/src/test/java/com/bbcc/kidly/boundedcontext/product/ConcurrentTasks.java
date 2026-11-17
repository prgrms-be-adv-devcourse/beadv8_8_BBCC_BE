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

final class ConcurrentTasks {

    private ConcurrentTasks() {
    }

    static void runConcurrently(List<Runnable> tasks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        try {
            awaitAll(startTogether(executor, tasks));
        } finally {
            executor.shutdown();
        }
    }

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
