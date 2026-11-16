package com.bbcc.kidly.gateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.gateway.support.IntegrationTest;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Flux;

@IntegrationTest
class SharedRequestRateLimiterIntegrationTest {

    @Autowired
    private ReactiveStringRedisTemplate redis;

    @Test
    @DisplayName("두 Gateway가 동시에 요청해도 같은 키의 한도를 공유한다")
    void sharesLimit() {
        var properties = new GlobalRateLimitProperties(20, Duration.ofHours(1), Duration.ofSeconds(1));
        var first = new SharedRequestRateLimiter(redis, properties);
        var second = new SharedRequestRateLimiter(redis, properties);

        var results = Flux.range(0, 60)
            .flatMap(i -> (i % 2 == 0 ? first : second).tryAcquire("shared"), 60)
            .collectList().block();

        assertThat(results).filteredOn(result -> result == 0).hasSize(20);
    }

    @Test
    @DisplayName("부족한 토큰의 소수가 보존되어 채워진 뒤 요청이 통과한다")
    void preservesFractionalTokens() throws InterruptedException {
        var properties = new GlobalRateLimitProperties(2, Duration.ofSeconds(2), Duration.ofSeconds(1));
        var limiter = new SharedRequestRateLimiter(redis, properties);
        assertThat(limiter.tryAcquire("fraction").block()).isZero();
        assertThat(limiter.tryAcquire("fraction").block()).isZero();
        Thread.sleep(500);
        assertThat(limiter.tryAcquire("fraction").block()).isPositive();
        Thread.sleep(600);
        assertThat(limiter.tryAcquire("fraction").block()).isZero();
    }
}
