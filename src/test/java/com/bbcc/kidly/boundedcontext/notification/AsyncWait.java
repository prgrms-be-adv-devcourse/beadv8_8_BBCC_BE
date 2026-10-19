package com.bbcc.kidly.boundedcontext.notification;

import java.time.Duration;
import java.util.function.BooleanSupplier;

/**
 * 비동기 발송이 끝나기를 기다린다. 조건이 5초 안에 만족되지 않으면 실패한다.
 */
final class AsyncWait {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final long POLL_MILLIS = 50;

    private AsyncWait() {
    }

    static void until(BooleanSupplier condition) {
        var deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError(TIMEOUT.toSeconds() + "초 안에 비동기 처리가 끝나지 않았습니다");
            }
            try {
                Thread.sleep(POLL_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("기다리는 중에 중단됐습니다", e);
            }
        }
    }
}
