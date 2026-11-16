package com.bbcc.kidly.gateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.gateway.support.AsyncWait;
import com.bbcc.kidly.gateway.support.GatewayTestSupport;
import com.bbcc.kidly.gateway.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Flux;

@IntegrationTest
class GlobalRateLimitIntegrationTest extends GatewayTestSupport {

    @Autowired
    private SharedRequestRateLimiter limiter;

    @Autowired
    private RateLimitSwitch rateLimitSwitch;

    @Autowired
    private ReactiveStringRedisTemplate redis;

    @AfterEach
    void restore() {
        redis.opsForValue().set(RateLimitSwitch.KEY, "true").block();
        AsyncWait.until(rateLimitSwitch::isEnabled);
    }

    @Test
    @DisplayName("한도를 넘은 IP는 429로 막고 다른 IP는 백엔드로 넘긴다")
    void limitsEachIp() {
        exhaust("203.0.113.201");
        var response = client.get().uri("/api/v1/products/1").header("X-Real-IP", "203.0.113.201").exchange();
        expectProblem(response, HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 많습니다", "잠시 후 다시 시도해 주세요.",
            "/api/v1/products/1");
        response.expectHeader().valueMatches(HttpHeaders.RETRY_AFTER, "[1-9][0-9]*");
        assertThat(productBackend.lastRequest()).isEmpty();
        client.get().uri("/api/v1/products/1").header("X-Real-IP", "203.0.113.202").exchange()
            .expectStatus().isOk();
    }

    @Test
    @DisplayName("전체 제한을 끄면 한도를 넘은 요청도 넘긴다")
    void disabledPasses() {
        exhaust("203.0.113.203");
        redis.opsForValue().set(RateLimitSwitch.KEY, "false").block();
        AsyncWait.until(() -> !rateLimitSwitch.isEnabled());
        client.get().uri("/api/v1/products/1").header("X-Real-IP", "203.0.113.203").exchange()
            .expectStatus().isOk();
    }

    private void exhaust(String ip) {
        Flux.range(0, 1000).concatMap(i -> limiter.tryAcquire("IP:" + ip)).blockLast();
    }
}
