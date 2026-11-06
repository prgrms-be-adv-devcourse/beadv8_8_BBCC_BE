package com.bbcc.kidly.gateway.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 키(회원 · IP)마다 토큰 버킷으로 요청 수를 센다(#816). 버킷은 capacity개로 시작하고 period마다 capacity개가 고르게 다시 찬다.
 * Gateway는 환경마다 한 대라 메모리에 둔다. 다시 뜨면 비고, 여러 대로 늘리면 대마다 따로 세므로 그때는 공용 저장소(#690)로 옮긴다.
 * 키가 maxKeys를 넘으면 다시 가득 찬(period 동안 쓰지 않은) 버킷을 지운다. 가득 찬 버킷은 새 버킷과 같아서 지워도 결과가 같다.
 * 지운 뒤에도 넘으면(아주 많은 IP에서 한꺼번에 옴) 새 키는 세지 않고 통과시킨다. 정상 사용자를 429로 막지 않으려는 것이다.
 */
final class RequestRateLimiter {

    private final int capacity;
    private final long periodNanos;
    private final int maxKeys;
    private final Clock clock;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final AtomicLong lastCleanupNanos;

    RequestRateLimiter(int capacity, Duration period, int maxKeys, Clock clock) {
        if (capacity <= 0 || period.isZero() || period.isNegative() || maxKeys <= 0) {
            throw new IllegalArgumentException("capacity · period · maxKeys는 0보다 커야 합니다");
        }
        this.capacity = capacity;
        this.periodNanos = period.toNanos();
        this.maxKeys = maxKeys;
        this.clock = clock;
        this.lastCleanupNanos = new AtomicLong(nowNanos());
    }

    /**
     * 요청 하나를 쓴다.
     *
     * @return 통과면 0, 막으면 다음 요청을 보낼 수 있을 때까지의 초(1 이상)
     */
    long tryAcquire(String key) {
        long now = nowNanos();
        Bucket bucket = buckets.get(key);
        if (bucket == null) {
            if (buckets.size() >= maxKeys && !cleanup(now)) {
                return 0;
            }
            bucket = buckets.computeIfAbsent(key, ignored -> new Bucket(capacity, now));
        }
        return bucket.tryAcquire(now, capacity, periodNanos);
    }

    int trackedKeys() {
        return buckets.size();
    }

    // 지운 뒤 자리가 났으면 true. 한 period에 한 번만 훑는다(요청마다 전체를 훑지 않게)
    private boolean cleanup(long now) {
        long last = lastCleanupNanos.get();
        if (now - last >= periodNanos && lastCleanupNanos.compareAndSet(last, now)) {
            buckets.values().removeIf(bucket -> bucket.isFull(now, capacity, periodNanos));
        }
        return buckets.size() < maxKeys;
    }

    private long nowNanos() {
        var instant = clock.instant();
        return instant.getEpochSecond() * 1_000_000_000L + instant.getNano();
    }

    private static final class Bucket {

        // 남은 요청 수. 소수까지 세어 고르게 다시 찬다
        private double tokens;
        private long updatedNanos;

        Bucket(int capacity, long now) {
            this.tokens = capacity;
            this.updatedNanos = now;
        }

        synchronized long tryAcquire(long now, int capacity, long periodNanos) {
            refill(now, capacity, periodNanos);
            if (tokens >= 1) {
                tokens -= 1;
                return 0;
            }
            double nanosPerToken = (double) periodNanos / capacity;
            long waitNanos = (long) Math.ceil((1 - tokens) * nanosPerToken);
            return Math.max(1, (waitNanos + 999_999_999L) / 1_000_000_000L);
        }

        synchronized boolean isFull(long now, int capacity, long periodNanos) {
            refill(now, capacity, periodNanos);
            return tokens >= capacity;
        }

        private void refill(long now, int capacity, long periodNanos) {
            long elapsed = now - updatedNanos;
            if (elapsed <= 0) {
                return;
            }
            tokens = Math.min(capacity, tokens + (double) elapsed * capacity / periodNanos);
            updatedNanos = now;
        }
    }
}
