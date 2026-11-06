package com.bbcc.kidly.gateway.ratelimit;

import com.bbcc.kidly.gateway.web.ProblemResponse;
import com.bbcc.kidly.shared.auth.PrincipalHeaders;
import com.bbcc.kidly.shared.auth.PrincipalType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.InetSocketAddress;
import java.time.Clock;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 검색어가 있는 상품 목록 요청(GET /api/v1/products?keyword=...)의 수를 제한한다(#816).
 * 검색 서비스가 검색어마다 임베딩 API(OpenAI)를 불러, 공개 API인데 호출량 · 비용을 부르는 쪽이 정하게 되기 때문이다.
 * 검색어 없는 둘러보기는 임베딩을 부르지 않으므로 세지 않는다.
 * 로그인했으면 주체(회원 · 관리자 · 자녀) ID로, 아니면 클라이언트 IP로 센다. 넘으면 429 ProblemDetail과 Retry-After(초)다.
 * IP는 nginx가 덮어쓰는 X-Real-IP다. X-Forwarded-For는 nginx가 클라이언트가 보낸 값 뒤에 덧붙여 첫 값을 지어낼 수 있어 쓰지 않는다.
 * X-Real-IP가 없으면(로컬 · nginx 없이) 연결 주소를 쓴다.
 * 주체 헤더는 AccessTokenFilter가 밖에서 온 값을 지우고 토큰으로 붙인 것이라, 이 필터는 그 뒤에 돈다.
 * IP · 회원 ID는 로그에 남기지 않는다. 막은 수는 메트릭 kidly.gateway.rate.limited(target=search)로 센다.
 */
@Component
class SearchRateLimitFilter implements GlobalFilter, Ordered {

    // RoleFilter(AccessTokenFilter.ORDER + 1) 다음
    static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 2;

    static final String REAL_IP = "X-Real-IP";
    private static final String PRODUCT_LIST_PATH = "/api/v1/products";
    private static final String KEYWORD = "keyword";

    private final RequestRateLimiter limiter;
    private final ProblemResponse problem;
    private final Counter rejected;

    SearchRateLimitFilter(SearchRateLimitProperties properties, ProblemResponse problem, MeterRegistry meterRegistry) {
        this.limiter = new RequestRateLimiter(properties.capacity(), properties.period(), properties.maxKeys(),
            Clock.systemUTC());
        this.problem = problem;
        this.rejected = Counter.builder("kidly.gateway.rate.limited")
            .description("요청 수 제한으로 429를 낸 요청 수")
            .tag("target", "search")
            .register(meterRegistry);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        var request = exchange.getRequest();
        if (!isKeywordSearch(request)) {
            return chain.filter(exchange);
        }
        long retryAfterSeconds = limiter.tryAcquire(keyOf(request));
        if (retryAfterSeconds == 0) {
            return chain.filter(exchange);
        }
        rejected.increment();
        return problem.tooManyRequests(exchange, retryAfterSeconds);
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

    private static String keyOf(ServerHttpRequest request) {
        var headers = request.getHeaders();
        for (PrincipalType type : PrincipalType.values()) {
            var id = headers.getFirst(PrincipalHeaders.idHeader(type));
            if (id != null) {
                return type.name() + ":" + id;
            }
        }
        return "IP:" + clientIp(request);
    }

    private static String clientIp(ServerHttpRequest request) {
        var realIp = request.getHeaders().getFirst(REAL_IP);
        if (realIp != null && !realIp.isBlank()) {
            return realIp.strip();
        }
        InetSocketAddress remote = request.getRemoteAddress();
        if (remote == null) {
            return "unknown";
        }
        return remote.getAddress() == null ? remote.getHostString() : remote.getAddress().getHostAddress();
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
