package com.bbcc.kidly.gateway.ratelimit;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bbcc.kidly.gateway.support.AsyncWait;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;

class RateLimitSwitchTest {

    @Test
    @DisplayName("Redis 읽기가 읽는 주기보다 오래 걸려도 바뀐 스위치를 계속 읽는다")
    void keepsRefreshingWhenReadIsSlowerThanPeriod() {
        var stored = new AtomicReference<>("true");
        var rateLimitSwitch = new RateLimitSwitch(slowRedis(stored, Duration.ofMillis(100)),
            new RateLimitProperties(Duration.ofMillis(20), null, null, null, null));
        rateLimitSwitch.start();
        try {
            stored.set("false");
            AsyncWait.until(() -> !rateLimitSwitch.isEnabled());
            stored.set("true");
            AsyncWait.until(rateLimitSwitch::isEnabled);
        } finally {
            rateLimitSwitch.stop();
        }
    }

    @SuppressWarnings("unchecked")
    private static ReactiveStringRedisTemplate slowRedis(AtomicReference<String> stored, Duration delay) {
        var redis = mock(ReactiveStringRedisTemplate.class);
        var values = (ReactiveValueOperations<String, String>) mock(ReactiveValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(RateLimitSwitch.KEY)).thenAnswer(ignored -> Mono.fromSupplier(stored::get).delayElement(delay));
        return redis;
    }
}
