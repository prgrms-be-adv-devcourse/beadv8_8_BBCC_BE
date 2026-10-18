package com.bbcc.kidly.boundedcontext.product;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 통합 테스트용: 두 작업을 실제 스레드 두 개로 겹쳐 실행해, 뒤의 작업이 앞의 커밋을 기다리는지 본다.
 * 앞의 작업은 바깥 트랜잭션 안에서 실행해 커밋 직전에 멈추고, 그동안 뒤의 작업을 시작한다.
 * 테스트 클래스패스의 컴포넌트 스캔에 잡혀 모든 통합 · 기능 테스트 컨텍스트에 똑같이 등록된다.
 */
@Component
class TransactionRace {

    // 뒤의 작업이 이 시간 안에 끝나면 앞의 커밋을 기다리지 않은 것으로 본다
    private static final long WAIT_MILLIS = 700;

    private final TransactionTemplate transactionTemplate;

    TransactionRace(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * 뒤의 작업이 앞의 커밋을 기다렸는지와, 뒤의 작업이 던진 예외(없으면 null)다.
     */
    record Outcome(boolean waited, Throwable error) {
    }

    // 앞의 작업을 커밋 직전에 멈춘 채 뒤의 작업을 시작하고, 뒤의 작업이 끝나지 않는지 본 뒤 앞을 커밋한다
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
            if (!held.await(10, TimeUnit.SECONDS)) {
                firstDone.get(1, TimeUnit.SECONDS);
                throw new IllegalStateException("앞의 작업이 10초 안에 끝나지 않았습니다");
            }
            Future<Throwable> secondDone = pool.submit(() -> {
                try {
                    second.run();
                    return null;
                } catch (RuntimeException e) {
                    return e;
                }
            });
            boolean waited = isStillRunning(secondDone);
            release.countDown();
            firstDone.get(10, TimeUnit.SECONDS);
            return new Outcome(waited, secondDone.get(10, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    private static boolean isStillRunning(Future<?> future) throws Exception {
        try {
            future.get(WAIT_MILLIS, TimeUnit.MILLISECONDS);
            return false;
        } catch (TimeoutException e) {
            return true;
        }
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
