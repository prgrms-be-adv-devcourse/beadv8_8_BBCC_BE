package com.bbcc.kidly.gateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

class RateLimitKeyResolversTest {

    private static final RateLimitProperties.Limit LIMIT = new RateLimitProperties.Limit(10, Duration.ofMinutes(1));
    private final RateLimitConfig config = new RateLimitConfig();

    @Test
    @DisplayName("회원 · IP별 한도는 로그인 주체가 없으면 X-Real-IP로 센다")
    void clientKeyIsIpWithoutPrincipal() {
        var resolver = config.clientKeyResolver(properties(true));

        assertThat(resolve(resolver, MockServerHttpRequest.get("/api/v1/orders").header("X-Real-IP", "203.0.113.1")))
            .isEqualTo("IP:203.0.113.1");
    }

    @Test
    @DisplayName("회원 · IP별 한도는 client-enabled가 false면 키가 없어 세지 않는다")
    void clientKeyIsEmptyWhenDisabled() {
        var resolver = config.clientKeyResolver(properties(false));

        assertThat(resolve(resolver, MockServerHttpRequest.get("/api/v1/orders"))).isNull();
    }

    @Test
    @DisplayName("검색 한도는 검색어가 있는 상품 목록만 센다")
    void searchKeyOnlyForKeywordSearch() {
        var resolver = config.searchKeyResolver();

        assertThat(resolve(resolver, search("/api/v1/products?keyword=swim"))).isEqualTo("search:IP:203.0.113.1");
        assertThat(resolve(resolver, search("/api/v1/products"))).isNull();
        assertThat(resolve(resolver, search("/api/v1/products?keyword=%20%20"))).isNull();
        assertThat(resolve(resolver, search("/api/v1/products/1?keyword=swim"))).isNull();
        assertThat(resolve(resolver, MockServerHttpRequest.post("/api/v1/products?keyword=swim"))).isNull();
    }

    @Test
    @DisplayName("검색 한도는 keyword가 여러 번 오면 쉼표로 이은 값으로 본다")
    void searchKeyJoinsRepeatedKeyword() {
        var resolver = config.searchKeyResolver();

        assertThat(resolve(resolver, search("/api/v1/products?keyword=&keyword=swim"))).isNotNull();
        assertThat(resolve(resolver, search("/api/v1/products?keyword=&keyword="))).isNotNull();
    }

    @Test
    @DisplayName("챗봇 한도는 질문(POST messages)만 센다")
    void chatKeyOnlyForQuestion() {
        var resolver = config.chatKeyResolver();

        assertThat(resolve(resolver, MockServerHttpRequest.post("/api/v1/chatbot/messages")
            .header("X-Real-IP", "203.0.113.1"))).isEqualTo("chat:IP:203.0.113.1");
        assertThat(resolve(resolver, MockServerHttpRequest.post("/api/v1/admin/chatbot/documents"))).isNull();
        assertThat(resolve(resolver, MockServerHttpRequest.put("/api/v1/admin/chatbot/documents/1"))).isNull();
    }

    @Test
    @DisplayName("한도는 1개 이상, 기간은 1초 이상이어야 한다")
    void rejectsInvalidLimit() {
        assertThatThrownBy(() -> config.searchRateLimiter(properties(true, new RateLimitProperties.Limit(10,
            Duration.ofMillis(500))))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> config.chatRateLimiter(properties(true, new RateLimitProperties.Limit(0,
            Duration.ofMinutes(1))))).isInstanceOf(IllegalArgumentException.class);
    }

    private static MockServerHttpRequest.BaseBuilder<?> search(String uri) {
        return MockServerHttpRequest.method(HttpMethod.GET, URI.create(uri)).header("X-Real-IP", "203.0.113.1");
    }

    private static String resolve(KeyResolver resolver, MockServerHttpRequest.BaseBuilder<?> request) {
        return resolver.resolve(MockServerWebExchange.from(request)).block();
    }

    private static RateLimitProperties properties(boolean clientEnabled) {
        return new RateLimitProperties(clientEnabled, LIMIT, LIMIT, LIMIT);
    }

    private static RateLimitProperties properties(boolean clientEnabled, RateLimitProperties.Limit limit) {
        return new RateLimitProperties(clientEnabled, limit, limit, limit);
    }
}
