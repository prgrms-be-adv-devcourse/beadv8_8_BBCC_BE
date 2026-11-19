package com.bbcc.kidly.gateway.ratelimit;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.Assert;
import reactor.core.publisher.Mono;

/**
 * 요청 수 제한은 Spring Cloud Gateway의 RequestRateLimiter 필터(application.yml)가 하고, 여기서는 한도별 RedisRateLimiter와
 * 센 기준(KeyResolver)을 만든다(#1628). 한도는 라우트마다 따로 센다. 여러 한도가 한 라우트를 지나므로(상품 목록의 회원 · IP별 한도 + 검색 한도)
 * 한도마다 설정이 고정된 리미터를 따로 둔다. yml의 redis-rate-limiter.* 인자는 라우트 ID로 모든 리미터에 묶여 서로 덮어쓰므로 쓰지 않는다.
 * 같은 라우트의 한도가 Redis 키(라우트 ID + 키)에서 섞이지 않도록 검색 · 챗봇 키에는 접두어를 붙인다.
 * 기본 필터 팩토리는 리미터 · KeyResolver를 하나씩만 받으므로 회원 · IP별 한도를 @Primary로 둔다.
 * KeyResolver가 빈 값을 주면 세지 않고 통과한다(deny-empty-key: false).
 */
@Configuration(proxyBeanMethods = false)
class RateLimitConfig {

    private static final String PRODUCT_LIST_PATH = "/api/v1/products";
    private static final String KEYWORD = "keyword";
    private static final String CHAT_MESSAGES_PATH = "/api/v1/chatbot/messages";

    @Bean
    @Primary
    RedisRateLimiter clientRateLimiter(RateLimitProperties properties) {
        return rateLimiter(properties.client());
    }

    @Bean
    RedisRateLimiter searchRateLimiter(RateLimitProperties properties) {
        return rateLimiter(properties.search());
    }

    @Bean
    RedisRateLimiter chatRateLimiter(RateLimitProperties properties) {
        return rateLimiter(properties.chat());
    }

    @Bean
    @Primary
    KeyResolver clientKeyResolver(RateLimitProperties properties) {
        return exchange -> properties.clientEnabled()
            ? Mono.just(RateLimitKeys.keyOf(exchange.getRequest()))
            : Mono.empty();
    }

    @Bean
    KeyResolver searchKeyResolver() {
        return exchange -> {
            var request = exchange.getRequest();
            return isKeywordSearch(request) ? Mono.just("search:" + RateLimitKeys.keyOf(request)) : Mono.empty();
        };
    }

    // chatbot-long 라우트에는 관리자 문서 저장(POST · PUT)도 있어 질문만 센다
    @Bean
    KeyResolver chatKeyResolver() {
        return exchange -> {
            var request = exchange.getRequest();
            var isQuestion = request.getMethod() == HttpMethod.POST
                && CHAT_MESSAGES_PATH.equals(request.getPath().value());
            return isQuestion ? Mono.just("chat:" + RateLimitKeys.keyOf(request)) : Mono.empty();
        };
    }

    // 공식 문서의 분 단위 설정이다. 토큰은 초마다 capacity개 차고 요청은 기간(초)만큼 쓰므로 기간마다 capacity번, 한꺼번에도 capacity번이다.
    // 토큰 수를 헤더로 내보내면 요청 수가 아니라 혼동만 되므로 X-RateLimit-* 헤더는 보내지 않는다
    private static RedisRateLimiter rateLimiter(RateLimitProperties.Limit limit) {
        var seconds = limit.period().toSeconds();
        Assert.isTrue(limit.capacity() >= 1 && seconds >= 1, "요청 수 제한은 한도 1개 이상, 기간 1초 이상이어야 합니다");
        var rateLimiter = new RedisRateLimiter(limit.capacity(), limit.capacity() * seconds, (int) seconds);
        rateLimiter.setIncludeHeaders(false);
        return rateLimiter;
    }

    // 검색 서비스가 받는 검색어와 같게 만들어 본다. 검색 서비스(@ModelAttribute)는 keyword가 여러 번 오면 쉼표로 이어 받고
    // (?keyword=&keyword= → ","), ProductListService가 앞뒤 공백을 뺀 값이 비어 있지 않으면 임베딩을 부른다
    private static boolean isKeywordSearch(ServerHttpRequest request) {
        if (request.getMethod() != HttpMethod.GET || !PRODUCT_LIST_PATH.equals(request.getPath().value())) {
            return false;
        }
        var keywords = request.getQueryParams().get(KEYWORD);
        return keywords != null && !keywords.isEmpty() && !String.join(",", keywords).isBlank();
    }
}
