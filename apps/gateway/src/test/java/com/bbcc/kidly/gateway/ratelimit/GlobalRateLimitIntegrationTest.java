package com.bbcc.kidly.gateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.gateway.support.AsyncWait;
import com.bbcc.kidly.gateway.support.GatewayTestSupport;
import com.bbcc.kidly.gateway.support.IntegrationTest;
import com.bbcc.kidly.gateway.support.TestTokens;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Flux;

@IntegrationTest
class GlobalRateLimitIntegrationTest extends GatewayTestSupport {

    @Autowired
    private SharedRequestRateLimiter limiter;

    @Autowired
    private RateLimitProperties properties;

    @Autowired
    private RateLimitSettings rateLimitSettings;

    @Autowired
    private ReactiveStringRedisTemplate redis;

    @Test
    @DisplayName("전체 버킷이 비면 503으로 막고 사용자 버킷을 차감하지 않는다")
    void totalLimitRejects() {
        emptyTotal();
        var response = client.get().uri("/api/v1/products/1").header("X-Real-IP", "203.0.113.210").exchange();
        expectProblem(response, HttpStatus.SERVICE_UNAVAILABLE, "서비스를 잠시 사용할 수 없습니다",
            "잠시 후 다시 시도해 주세요.", "/api/v1/products/1");
        response.expectHeader().valueMatches(HttpHeaders.RETRY_AFTER, "[1-9][0-9]*");
        assertThat(productBackend.lastRequest()).isEmpty();
        assertThat(redis.hasKey("kidly:gateway:rate-limit:client:IP:203.0.113.210").block()).isFalse();
    }

    @Test
    @DisplayName("스위치를 끄면 전체 버킷이 비어도 통과한다")
    void disabledBypassesTotal() {
        emptyTotal();
        rateLimitSettings.set(new RateLimitSettings.Change(false, null, null)).block();
        client.get().uri("/api/v1/products/1").exchange().expectStatus().isOk();
    }

    @Test
    @DisplayName("관리자 설정 경로는 전체 버킷이 비어도 통과한다")
    void settingsPathBypassesTotal() {
        emptyTotal();
        var now = Instant.now();
        var admin = TestTokens.issueAdmin(12, now, now.plusSeconds(1800));
        client.get().uri(GlobalRateLimitFilter.SETTINGS_PATH).headers(h -> h.setBearerAuth(admin)).exchange()
            .expectStatus().isOk();
    }

    private void emptyTotal() {
        var script = RedisScript.of("""
            local time = redis.call('TIME')
            local now = time[1] * 1000 + math.floor(time[2] / 1000)
            redis.call('HSET', KEYS[1], 'tokens', '0', 'updated', now + 60000)
            return 1
            """, Long.class);
        redis.execute(script, List.of("kidly:gateway:rate-limit:total"), List.of()).blockLast();
    }

    @AfterEach
    void restore() {
        redis.<String, String>opsForHash().put(RateLimitSettings.KEY, "enabled", "true").block();
        AsyncWait.until(() -> rateLimitSettings.snapshot().enabled());
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
        redis.<String, String>opsForHash().put(RateLimitSettings.KEY, "enabled", "false").block();
        AsyncWait.until(() -> !rateLimitSettings.snapshot().enabled());
        client.get().uri("/api/v1/products/1").header("X-Real-IP", "203.0.113.203").exchange()
            .expectStatus().isOk();
    }

    private void exhaust(String ip) {
        Flux.range(0, 1000).concatMap(i -> limiter.tryAcquire("client", "IP:" + ip, properties.client())).blockLast();
    }
}
