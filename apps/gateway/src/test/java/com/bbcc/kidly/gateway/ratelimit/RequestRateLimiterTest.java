package com.bbcc.kidly.gateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RequestRateLimiterTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-06T00:00:00Z"));

    @Test
    @DisplayName("키마다 capacity번까지 받고, 넘으면 다음 요청까지 남은 초를 돌려준다")
    void limitsPerKey() {
        var limiter = new RequestRateLimiter(3, Duration.ofMinutes(1), 100, clock);

        assertThat(limiter.tryAcquire("a")).isZero();
        assertThat(limiter.tryAcquire("a")).isZero();
        assertThat(limiter.tryAcquire("a")).isZero();
        // 하나가 다시 차는 데 60초 / 3 = 20초
        assertThat(limiter.tryAcquire("a")).isEqualTo(20);
        assertThat(limiter.tryAcquire("b")).isZero();
    }

    @Test
    @DisplayName("시간이 지나면 지난 만큼 고르게 다시 차고, capacity보다 많이 쌓이지 않는다")
    void refillsGradually() {
        var limiter = new RequestRateLimiter(3, Duration.ofMinutes(1), 100, clock);
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire("a");
        }

        clock.advance(Duration.ofSeconds(15));
        assertThat(limiter.tryAcquire("a")).isEqualTo(5);

        clock.advance(Duration.ofSeconds(5));
        assertThat(limiter.tryAcquire("a")).isZero();
        assertThat(limiter.tryAcquire("a")).isEqualTo(20);

        clock.advance(Duration.ofHours(1));
        for (int i = 0; i < 3; i++) {
            assertThat(limiter.tryAcquire("a")).isZero();
        }
        assertThat(limiter.tryAcquire("a")).isPositive();
    }

    @Test
    @DisplayName("키가 상한에 닿으면 다시 가득 찬 버킷을 지우고 새 키를 센다")
    void evictsFullBucketsAtMaxKeys() {
        var limiter = new RequestRateLimiter(1, Duration.ofMinutes(1), 2, clock);
        limiter.tryAcquire("a");
        limiter.tryAcquire("b");

        clock.advance(Duration.ofMinutes(1));
        assertThat(limiter.tryAcquire("c")).isZero();
        assertThat(limiter.trackedKeys()).isEqualTo(1);
        assertThat(limiter.tryAcquire("c")).isPositive();
    }

    @Test
    @DisplayName("지울 버킷이 없어 상한을 넘으면 새 키는 세지 않고 통과시킨다. 이미 센 키는 계속 막는다")
    void passesNewKeysWhenFull() {
        var limiter = new RequestRateLimiter(1, Duration.ofMinutes(1), 2, clock);
        limiter.tryAcquire("a");
        limiter.tryAcquire("b");

        assertThat(limiter.tryAcquire("c")).isZero();
        assertThat(limiter.tryAcquire("c")).isZero();
        assertThat(limiter.trackedKeys()).isEqualTo(2);
        assertThat(limiter.tryAcquire("a")).isPositive();
    }

    @Test
    @DisplayName("capacity · period · maxKeys가 0 이하면 만들지 않는다")
    void rejectsInvalidSettings() {
        assertThatThrownBy(() -> new RequestRateLimiter(0, Duration.ofMinutes(1), 1, clock))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RequestRateLimiter(1, Duration.ZERO, 1, clock))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RequestRateLimiter(1, Duration.ofMinutes(1), 0, clock))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
