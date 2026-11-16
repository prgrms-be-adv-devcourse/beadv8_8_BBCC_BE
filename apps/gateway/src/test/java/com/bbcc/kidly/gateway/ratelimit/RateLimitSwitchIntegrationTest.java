package com.bbcc.kidly.gateway.ratelimit;

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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import reactor.core.publisher.Flux;

@IntegrationTest
class RateLimitSwitchIntegrationTest extends GatewayTestSupport {

    private static final String PATH = "/api/v1/admin/gateway/rate-limit";

    @Autowired
    private ReactiveStringRedisTemplate redis;

    @Autowired
    private RateLimitSwitch rateLimitSwitch;

    @Autowired
    private RateLimitProperties properties;

    @Autowired
    private SharedRequestRateLimiter limiter;

    @AfterEach
    void restore() {
        redis.opsForValue().set(RateLimitSwitch.KEY, "true").block();
        AsyncWait.until(rateLimitSwitch::isEnabled);
    }

    @Test
    @DisplayName("관리자는 스위치를 조회하고 변경하며 다른 Gateway도 변경을 읽는다")
    void administratorChangesSwitch() {
        var other = new RateLimitSwitch(redis, properties);
        other.start();
        try {
            changesSwitch(other);
        } finally {
            other.stop();
        }
    }

    private void changesSwitch(RateLimitSwitch other) {
        var now = Instant.now();
        var admin = TestTokens.issueAdmin(11, now, now.plusSeconds(1800));
        client.get().uri(PATH).headers(headers -> headers.setBearerAuth(admin)).exchange()
            .expectStatus().isOk().expectBody().jsonPath("$.enabled").isEqualTo(true);
        client.put().uri(PATH).headers(headers -> headers.setBearerAuth(admin))
            .contentType(MediaType.APPLICATION_JSON).bodyValue("{\"enabled\":false}").exchange()
            .expectStatus().isOk().expectBody().jsonPath("$.enabled").isEqualTo(false);
        client.get().uri(PATH).headers(headers -> headers.setBearerAuth(admin)).exchange()
            .expectStatus().isOk().expectBody().jsonPath("$.enabled").isEqualTo(false);
        AsyncWait.until(() -> !other.isEnabled());
    }

    @Test
    @DisplayName("Redis 값이 false가 아니면 잘못 쓴 값이어도 켜진다")
    void onlyFalseDisables() {
        redis.opsForValue().set(RateLimitSwitch.KEY, "false").block();
        AsyncWait.until(() -> !rateLimitSwitch.isEnabled());

        redis.opsForValue().set(RateLimitSwitch.KEY, "1").block();

        AsyncWait.until(rateLimitSwitch::isEnabled);
    }

    @Test
    @DisplayName("관리자가 아니면 스위치를 읽거나 바꾸지 못한다")
    void rejectsNonAdmins() {
        var now = Instant.now();
        var member = TestTokens.issue(10, List.of("USER"), now, now.plusSeconds(1800));
        expectUnauthorized(client.get().uri(PATH).exchange(), PATH);
        expectForbidden(client.get().uri(PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + member).exchange(), PATH);
    }

    @Test
    @DisplayName("본문이 없거나 JSON 또는 enabled가 없으면 400이다")
    void rejectsInvalidBody() {
        var now = Instant.now();
        var admin = TestTokens.issueAdmin(11, now, now.plusSeconds(1800));
        for (var body : List.of("", "{}", "not-json")) {
            expectBadRequest(client.put().uri(PATH).headers(headers -> headers.setBearerAuth(admin))
                .contentType(MediaType.APPLICATION_JSON).bodyValue(body).exchange(), PATH);
        }
    }

    @Test
    @DisplayName("관리자 스위치 경로는 한도를 넘긴 관리자도 이용할 수 있다")
    void switchPathBypassesLimit() {
        var now = Instant.now();
        var admin = TestTokens.issueAdmin(12, now, now.plusSeconds(1800));
        Flux.range(0, properties.client().capacity())
            .concatMap(i -> limiter.tryAcquire("client", "ADMIN:12", properties.client())).blockLast();
        client.get().uri(PATH).headers(headers -> headers.setBearerAuth(admin)).exchange()
            .expectStatus().isOk();
        client.get().uri("/api/v1/products/1").headers(headers -> headers.setBearerAuth(admin)).exchange()
            .expectStatus().isEqualTo(429);
    }
}
