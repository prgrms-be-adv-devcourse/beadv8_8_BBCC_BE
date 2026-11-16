package com.bbcc.kidly.gateway.ratelimit;

import com.bbcc.kidly.gateway.web.ProblemResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 클라이언트별 요청과 전체 요청을 함께 제한해 한 클라이언트의 폭주와 다수 클라이언트의 총부하에서 백엔드를 보호한다(#1417).
 * 검색 · 챗봇 필터와 같은 키 · 429 응답 · 순서다(주체 헤더가 붙은 뒤). 설정 경로는 세지 않아 과부하 중에도 관리자가 끌 수 있다.
 */
@Component
class GlobalRateLimitFilter implements GlobalFilter, Ordered {

    static final String SETTINGS_PATH = "/api/v1/admin/gateway/rate-limit";
    private final SharedRequestRateLimiter limiter;
    private final RateLimitSettings settings;
    private final ProblemResponse problem;
    private final Counter clientRejected;
    private final Counter totalRejected;

    GlobalRateLimitFilter(SharedRequestRateLimiter limiter, RateLimitSettings settings,
        ProblemResponse problem, MeterRegistry registry) {
        this.limiter = limiter;
        this.settings = settings;
        this.problem = problem;
        this.clientRejected = Counter.builder("kidly.gateway.rate.limited").tag("target", "client").register(registry);
        this.totalRejected = Counter.builder("kidly.gateway.rate.limited").tag("target", "total").register(registry);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        var request = exchange.getRequest();
        var snapshot = settings.snapshot();
        if (!snapshot.enabled() || SETTINGS_PATH.equals(request.getPath().value())) {
            return chain.filter(exchange);
        }
        var client = new SharedRequestRateLimiter.Bucket("client", RateLimitKeys.keyOf(request), snapshot.client());
        var total = new SharedRequestRateLimiter.Bucket("total", null, snapshot.total());
        return limiter.tryAcquire(List.of(client, total)).flatMap(result -> {
            if (result instanceof SharedRequestRateLimiter.Result.Rejected denied) {
                if (denied.bucket().equals(total)) {
                    totalRejected.increment();
                    return problem.serviceUnavailable(exchange, denied.retryAfterSeconds());
                }
                clientRejected.increment();
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
