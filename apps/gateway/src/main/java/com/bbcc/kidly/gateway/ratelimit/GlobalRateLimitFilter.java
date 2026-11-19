package com.bbcc.kidly.gateway.ratelimit;

import com.bbcc.kidly.gateway.web.ProblemResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 클라이언트(회원 · IP)별로 모든 요청의 수를 제한해 한 클라이언트의 폭주에서 백엔드를 보호한다(#1417).
 * 검색 · 챗봇 필터와 같은 키 · 429 응답 · 순서다(주체 헤더가 붙은 뒤). 서버 전체의 과부하는 Gateway가 아니라 앞단(Traefik)이 막는다.
 * kidly.gateway.rate-limit.client-enabled가 false면 Redis를 부르지 않고 통과시킨다(부하테스트용, 재시작해야 바뀐다).
 * 막은 수는 메트릭 kidly.gateway.rate.limited(target=client)로 센다.
 */
@Component
class GlobalRateLimitFilter implements GlobalFilter, Ordered {

    private final SharedRequestRateLimiter limiter;
    private final RateLimitProperties properties;
    private final ProblemResponse problem;
    private final Counter rejected;

    GlobalRateLimitFilter(SharedRequestRateLimiter limiter, RateLimitProperties properties,
        ProblemResponse problem, MeterRegistry registry) {
        this.limiter = limiter;
        this.properties = properties;
        this.problem = problem;
        this.rejected = Counter.builder("kidly.gateway.rate.limited")
            .description("요청 수 제한으로 막은 요청 수(429)")
            .tag("target", "client")
            .register(registry);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!properties.clientEnabled()) {
            return chain.filter(exchange);
        }
        return limiter.tryAcquire("client", RateLimitKeys.keyOf(exchange.getRequest()), properties.client())
            .flatMap(result -> {
                if (result instanceof SharedRequestRateLimiter.Result.Rejected denied) {
                    rejected.increment();
                    return problem.tooManyRequests(exchange, denied.retryAfterSeconds());
                }
                return chain.filter(exchange);
            });
    }

    @Override
    public int getOrder() {
        return SearchRateLimitFilter.ORDER;
    }
}
