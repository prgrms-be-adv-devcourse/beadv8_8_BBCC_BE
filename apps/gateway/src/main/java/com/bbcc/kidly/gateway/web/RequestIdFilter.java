package com.bbcc.kidly.gateway.web;

import java.util.UUID;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * 요청마다 새 요청 ID를 만들어 요청 헤더에 덮어쓰고 응답 헤더로도 돌려준다(#683). 클라이언트가 보낸 값은 버린다(백엔드는
 * UUID면 이어 쓰므로 같은 값을 보낸 요청끼리 로그가 섞인다).
 * 응답 헤더는 beforeCommit에서 set한다: 백엔드도 같은 이름으로 응답 헤더를 붙이고 Spring Cloud Gateway가 백엔드 응답 헤더를
 * 그대로 더해 붙이므로, 더 먼저 붙이면 값이 두 개가 된다.
 * 헤더 이름은 common RequestLoggingFilter.TRACE_ID_HEADER와 같은 문자열이다(Gateway는 common에 의존하지 않는다).
 */
class RequestIdFilter implements WebFilter {

    static final String HEADER = "X-Request-Id";
    // 서비스 MDC · Alloy · Grafana가 찾는 이름이라 로그 키는 traceId다
    static final String LOG_KEY = "traceId";
    static final String ATTRIBUTE = RequestIdFilter.class.getName() + ".requestId";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        var requestId = UUID.randomUUID().toString();
        var mutated = exchange.mutate()
            .request(exchange.getRequest().mutate().header(HEADER, requestId).build())
            .build();
        mutated.getAttributes().put(ATTRIBUTE, requestId);
        mutated.getResponse().beforeCommit(() -> {
            mutated.getResponse().getHeaders().set(HEADER, requestId);
            return Mono.empty();
        });
        return chain.filter(mutated);
    }

    static String requestId(ServerWebExchange exchange) {
        return exchange.getAttribute(ATTRIBUTE);
    }
}
