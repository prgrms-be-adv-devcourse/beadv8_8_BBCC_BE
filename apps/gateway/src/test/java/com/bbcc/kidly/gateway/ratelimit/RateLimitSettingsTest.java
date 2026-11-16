package com.bbcc.kidly.gateway.ratelimit;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bbcc.kidly.gateway.support.AsyncWait;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveHashOperations;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;

class RateLimitSettingsTest {

    @Test
    @DisplayName("Redis 읽기가 읽는 주기보다 오래 걸려도 바뀐 설정을 계속 읽는다")
    void keepsRefreshingWhenReadIsSlowerThanPeriod() {
        var stored = new AtomicReference<>("true");
        var rateLimitSettings = new RateLimitSettings(slowRedis(stored, Duration.ofMillis(100)),
            new RateLimitProperties(Duration.ofMillis(20), null, null, null, null));
        rateLimitSettings.start();
        try {
            stored.set("false");
            AsyncWait.until(() -> !rateLimitSettings.snapshot().enabled());
            stored.set("true");
            AsyncWait.until(() -> rateLimitSettings.snapshot().enabled());
        } finally {
            rateLimitSettings.stop();
        }
    }

    @SuppressWarnings("unchecked")
    private static ReactiveStringRedisTemplate slowRedis(AtomicReference<String> stored, Duration delay) {
        var redis = mock(ReactiveStringRedisTemplate.class);
        var values = (ReactiveHashOperations<String, String, String>) mock(ReactiveHashOperations.class);
        when(redis.<String, String>opsForHash()).thenReturn(values);
        when(values.entries(RateLimitSettings.KEY)).thenAnswer(ignored ->
            Mono.fromSupplier(() -> Map.entry("enabled", stored.get())).delayElement(delay).flux());
        return redis;
    }
}
