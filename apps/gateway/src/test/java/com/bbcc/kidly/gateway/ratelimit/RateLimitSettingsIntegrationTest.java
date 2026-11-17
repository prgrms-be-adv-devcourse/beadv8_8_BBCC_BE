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
class RateLimitSettingsIntegrationTest extends GatewayTestSupport {

    private static final String PATH = "/api/v1/admin/gateway/rate-limit";

    @Autowired
    private ReactiveStringRedisTemplate redis;

    @Autowired
    private RateLimitSettings rateLimitSettings;

    @Autowired
    private RateLimitProperties properties;

    @Autowired
    private SharedRequestRateLimiter limiter;

    @AfterEach
    void restore() {
        redis.delete(RateLimitSettings.KEY).block();
        AsyncWait.until(() -> rateLimitSettings.snapshot().enabled()
            && rateLimitSettings.snapshot().client().equals(properties.client())
            && rateLimitSettings.snapshot().total().equals(properties.total()));
    }

    @Test
    @DisplayName("관리자는 설정을 조회하고 끄며 다른 Gateway도 변경을 읽는다")
    void administratorDisables() {
        var other = new RateLimitSettings(redis, properties);
        other.start();
        try {
            disables(other);
        } finally {
            other.stop();
        }
    }

    private void disables(RateLimitSettings other) {
        var now = Instant.now();
        var admin = TestTokens.issueAdmin(11, now, now.plusSeconds(1800));
        client.get().uri(PATH).headers(headers -> headers.setBearerAuth(admin)).exchange()
            .expectStatus().isOk().expectBody().jsonPath("$.enabled").isEqualTo(true)
            .jsonPath("$.client.capacity").isEqualTo(properties.client().capacity())
            .jsonPath("$.total.capacity").isEqualTo(properties.total().capacity());
        client.put().uri(PATH).headers(headers -> headers.setBearerAuth(admin))
            .contentType(MediaType.APPLICATION_JSON).bodyValue("{\"enabled\":false}").exchange()
            .expectStatus().isOk().expectBody().jsonPath("$.enabled").isEqualTo(false);
        client.get().uri(PATH).headers(headers -> headers.setBearerAuth(admin)).exchange()
            .expectStatus().isOk().expectBody().jsonPath("$.enabled").isEqualTo(false);
        AsyncWait.until(() -> !other.snapshot().enabled());
    }

    @Test
    @DisplayName("부분 변경은 다른 한도를 보존하고 다른 Gateway에도 반영된다")
    void changesTotal() {
        var other = new RateLimitSettings(redis, properties);
        other.start();
        try {
            var now = Instant.now();
            var admin = TestTokens.issueAdmin(11, now, now.plusSeconds(1800));
            client.put().uri(PATH).headers(h -> h.setBearerAuth(admin)).contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"total\":{\"capacity\":800,\"period\":\"PT1S\"}}").exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.enabled").isEqualTo(true)
                .jsonPath("$.client.capacity").isEqualTo(properties.client().capacity())
                .jsonPath("$.total.capacity").isEqualTo(800).jsonPath("$.total.period").isEqualTo("PT1S");
            client.get().uri(PATH).headers(h -> h.setBearerAuth(admin)).exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.total.capacity").isEqualTo(800);
            AsyncWait.until(() -> other.snapshot().total().capacity() == 800);
        } finally {
            other.stop();
        }
    }

    @Test
    @DisplayName("변경한 전체 한도가 실제 요청에 적용된다")
    void changedTotalLimitsRequests() {
        var now = Instant.now();
        var admin = TestTokens.issueAdmin(11, now, now.plusSeconds(1800));
        client.put().uri(PATH).headers(h -> h.setBearerAuth(admin)).contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"total\":{\"capacity\":1,\"period\":\"PT1H\"}}").exchange().expectStatus().isOk();
        client.get().uri("/api/v1/products/1").exchange().expectStatus().isOk();
        client.get().uri("/api/v1/products/1").exchange().expectStatus().isEqualTo(503)
            .expectHeader().exists(HttpHeaders.RETRY_AFTER);
        client.get().uri(PATH).headers(h -> h.setBearerAuth(admin)).exchange().expectStatus().isOk();
    }

    @Test
    @DisplayName("변경한 클라이언트 한도가 실제 요청에 적용된다")
    void changedClientLimitsRequests() {
        var now = Instant.now();
        var admin = TestTokens.issueAdmin(11, now, now.plusSeconds(1800));
        client.put().uri(PATH).headers(h -> h.setBearerAuth(admin)).contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"client\":{\"capacity\":1,\"period\":\"PT1H\"}}").exchange().expectStatus().isOk();
        client.get().uri("/api/v1/products/1").exchange().expectStatus().isOk();
        client.get().uri("/api/v1/products/1").exchange().expectStatus().isEqualTo(429)
            .expectHeader().exists(HttpHeaders.RETRY_AFTER);
    }

    @Test
    @DisplayName("잘못된 해시 한도는 기본값을 쓴다")
    void invalidHashUsesDefault() {
        var hash = redis.<String, String>opsForHash();
        for (var value : List.of("abc", "0")) {
            hash.put(RateLimitSettings.KEY, "client-capacity", "9").block();
            hash.put(RateLimitSettings.KEY, "client-period-ms", "1000").block();
            AsyncWait.until(() -> rateLimitSettings.snapshot().client().capacity() == 9);
            hash.put(RateLimitSettings.KEY, "client-capacity", value).block();
            hash.put(RateLimitSettings.KEY, "client-period-ms", "1000").block();
            AsyncWait.until(() -> rateLimitSettings.snapshot().client().equals(properties.client()));
        }
        hash.put(RateLimitSettings.KEY, "total-capacity", "1").block();
        hash.put(RateLimitSettings.KEY, "total-period-ms", "1000").block();
        AsyncWait.until(() -> rateLimitSettings.snapshot().total().capacity() == 1);
        hash.remove(RateLimitSettings.KEY, "total-period-ms").block();
        AsyncWait.until(() -> rateLimitSettings.snapshot().total().equals(properties.total()));
    }

    @Test
    @DisplayName("Redis 값이 false가 아니면 잘못 쓴 값이어도 켜진다")
    void onlyFalseDisables() {
        redis.<String, String>opsForHash().put(RateLimitSettings.KEY, "enabled", "false").block();
        AsyncWait.until(() -> !rateLimitSettings.snapshot().enabled());

        redis.<String, String>opsForHash().put(RateLimitSettings.KEY, "enabled", "1").block();

        AsyncWait.until(() -> rateLimitSettings.snapshot().enabled());
    }

    @Test
    @DisplayName("관리자가 아니면 설정을 읽거나 바꾸지 못한다")
    void rejectsNonAdmins() {
        var now = Instant.now();
        var member = TestTokens.issue(10, List.of("USER"), now, now.plusSeconds(1800));
        expectUnauthorized(client.get().uri(PATH).exchange(), PATH);
        expectForbidden(client.get().uri(PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + member).exchange(), PATH);
    }

    @Test
    @DisplayName("본문이 없거나 JSON이 아니거나 바꿀 칸이 없거나 한도가 잘못되면 400이다")
    void rejectsInvalidBody() {
        var now = Instant.now();
        var admin = TestTokens.issueAdmin(11, now, now.plusSeconds(1800));
        for (var body : List.of("", "{}", "not-json", "{\"total\":{\"capacity\":1}}",
            "{\"total\":{\"capacity\":0,\"period\":\"PT1S\"}}",
            "{\"total\":{\"capacity\":1,\"period\":\"PT0S\"}}")) {
            expectBadRequest(client.put().uri(PATH).headers(headers -> headers.setBearerAuth(admin))
                .contentType(MediaType.APPLICATION_JSON).bodyValue(body).exchange(), PATH);
        }
    }

    @Test
    @DisplayName("관리자 설정 경로는 한도를 넘긴 관리자도 이용할 수 있다")
    void settingsPathBypassesLimit() {
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
