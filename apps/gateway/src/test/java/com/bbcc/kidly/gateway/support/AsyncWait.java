package com.bbcc.kidly.gateway.support;

import java.time.Duration;
import java.util.function.BooleanSupplier;

/**
 * 비동기 처리(메일 발송, Kafka 수신 등)가 끝나기를 기다린다. 조건이 시간 안에 만족되지 않으면 실패한다.
 * Awaitility 대신 쓴다(회의 10-29 안건 3).
 */
public final class AsyncWait {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final long POLL_MILLIS = 50;

    private AsyncWait() {
    }

    public static void until(BooleanSupplier condition) {
        until(TIMEOUT, condition);
    }

    // Kafka 수신처럼 5초가 빠듯한 경우 기다리는 시간을 늘린다
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
