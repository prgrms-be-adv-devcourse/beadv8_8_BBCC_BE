package com.bbcc.kidly.gateway.ratelimit;

import com.bbcc.kidly.gateway.web.ProblemResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 챗봇 질문(POST /api/v1/chatbot/messages)의 수를 제한한다(#1191). 질문마다 챗봇 서비스가 OpenAI를 불러 비용이 든다.
 * 키 · 429 응답 · 실행 순서는 SearchRateLimitFilter와 같고 한도만 따로 센다. 관리자 챗봇 경로는 세지 않는다.
 * 막은 수는 메트릭 kidly.gateway.rate.limited(target=chat)로 센다.
 */
@Component
class ChatRateLimitFilter implements GlobalFilter, Ordered {

    private static final String MESSAGES_PATH = "/api/v1/chatbot/messages";

    private final RequestRateLimiter limiter;
    private final ProblemResponse problem;
    private final Counter rejected;

    ChatRateLimitFilter(ChatRateLimitProperties properties, ProblemResponse problem, MeterRegistry meterRegistry) {
        this.limiter = new RequestRateLimiter(properties.capacity(), properties.period(), properties.maxKeys(),
            Clock.systemUTC());
        this.problem = problem;
        this.rejected = Counter.builder("kidly.gateway.rate.limited")
            .description("요청 수 제한으로 429를 낸 요청 수")
            .tag("target", "chat")
            .register(meterRegistry);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        var request = exchange.getRequest();
        if (request.getMethod() != HttpMethod.POST || !MESSAGES_PATH.equals(request.getPath().value())) {
            return chain.filter(exchange);
        }
        long retryAfterSeconds = limiter.tryAcquire(RateLimitKeys.keyOf(request));
        if (retryAfterSeconds == 0) {
            return chain.filter(exchange);
        }
        rejected.increment();
        return problem.tooManyRequests(exchange, retryAfterSeconds);
    }

    @Override
    public int getOrder() {
        return SearchRateLimitFilter.ORDER;
    }
}
