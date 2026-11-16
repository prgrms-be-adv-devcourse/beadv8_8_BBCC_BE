package com.bbcc.kidly.gateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.gateway.support.IntegrationTest;
import java.time.Duration;
import java.util.List;
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
    @DisplayName("어느 버킷이 비어도 다른 버킷은 차감하지 않는다")
    void rejectsAtomically() {
        var limit = new RateLimitProperties.Limit(1, Duration.ofHours(1));
        var limiter = new SharedRequestRateLimiter(redis);
        for (int empty = 0; empty < 2; empty++) {
            var first = new SharedRequestRateLimiter.Bucket("client", "a" + empty, limit);
            var second = new SharedRequestRateLimiter.Bucket("total", "b" + empty, limit);
            var buckets = List.of(first, second);
            limiter.tryAcquire(List.of(buckets.get(empty))).block();
            var rejected = limiter.tryAcquire(buckets).block();
            assertThat(rejected).isInstanceOf(SharedRequestRateLimiter.Result.Rejected.class);
            assertThat(((SharedRequestRateLimiter.Result.Rejected) rejected).bucket()).isEqualTo(buckets.get(empty));
            assertThat(limiter.tryAcquire(List.of(buckets.get(1 - empty))).block())
                .isInstanceOf(SharedRequestRateLimiter.Result.Allowed.class);
        }
    }

    @Test
    @DisplayName("서로 다른 사용자가 두 Gateway로 동시에 보내도 전체 한도를 공유한다")
    void sharesTotalAcrossGateways() {
        var client = new RateLimitProperties.Limit(1, Duration.ofHours(1));
        var total = new RateLimitProperties.Limit(20, Duration.ofHours(1));
        var first = new SharedRequestRateLimiter(redis);
        var second = new SharedRequestRateLimiter(redis);
        var results = Flux.range(0, 60).flatMap(i -> (i % 2 == 0 ? first : second).tryAcquire(List.of(
            new SharedRequestRateLimiter.Bucket("client", "concurrent" + i, client),
            new SharedRequestRateLimiter.Bucket("total", null, total))), 60).collectList().block();
        assertThat(results).filteredOn(result -> result instanceof SharedRequestRateLimiter.Result.Allowed).hasSize(20);
    }

    @Test
    @DisplayName("두 Gateway가 동시에 요청해도 같은 키의 한도를 공유한다")
    void sharesLimit() {
        var properties = new RateLimitProperties.Limit(20, Duration.ofHours(1));
        var first = new SharedRequestRateLimiter(redis);
        var second = new SharedRequestRateLimiter(redis);

        var results = Flux.range(0, 60)
            .flatMap(i -> (i % 2 == 0 ? first : second).tryAcquire("client", "shared", properties), 60)
            .collectList().block();

        assertThat(results).filteredOn(result -> result instanceof SharedRequestRateLimiter.Result.Allowed).hasSize(20);
    }

    @Test
    @DisplayName("부족한 토큰의 소수가 보존되어 채워진 뒤 요청이 통과한다")
    void preservesFractionalTokens() throws InterruptedException {
        var properties = new RateLimitProperties.Limit(2, Duration.ofSeconds(2));
        var limiter = new SharedRequestRateLimiter(redis);
        assertThat(limiter.tryAcquire("client", "fraction", properties).block())
            .isInstanceOf(SharedRequestRateLimiter.Result.Allowed.class);
        assertThat(limiter.tryAcquire("client", "fraction", properties).block())
            .isInstanceOf(SharedRequestRateLimiter.Result.Allowed.class);
        Thread.sleep(500);
        assertThat(limiter.tryAcquire("client", "fraction", properties).block())
            .isInstanceOf(SharedRequestRateLimiter.Result.Rejected.class);
        Thread.sleep(600);
        assertThat(limiter.tryAcquire("client", "fraction", properties).block())
            .isInstanceOf(SharedRequestRateLimiter.Result.Allowed.class);
    }
}
