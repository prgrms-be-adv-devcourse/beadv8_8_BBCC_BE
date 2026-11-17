package com.bbcc.kidly.support;

import java.time.Duration;
import java.util.function.BooleanSupplier;

public final class AsyncWait {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final long POLL_MILLIS = 50;

    private AsyncWait() {
    }

    public static void until(BooleanSupplier condition) {
        until(TIMEOUT, condition);
    }

    public static void until(Duration timeout, BooleanSupplier condition) {
        var deadline = System.nanoTime() + timeout.toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError(timeout.toSeconds() + "초 안에 비동기 처리가 끝나지 않았습니다");
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
