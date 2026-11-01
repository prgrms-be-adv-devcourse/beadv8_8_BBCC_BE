package com.bbcc.kidly.gateway.web;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.web.server.firewall.ServerExchangeRejectedException;
import org.springframework.security.web.server.firewall.StrictServerWebExchangeFirewall;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * 정규화되지 않거나 위험한 요청을 400으로 막는다(StrictServerWebExchangeFirewall 기본값).
 * 예: /a/../admin · /./ · %2e · // · %2f · %5c · ; · %25 · 허용하지 않은 메서드(TRACE 등).
 * RoleFilter · 라우트는 정규화되지 않은 경로를 그대로 보고 판단하는데, 백엔드(Tomcat)는 정규화해서 받으므로
 * 그냥 두면 역할 검사와 /internal/** 차단을 비껴간다.
 * CORS보다 먼저 돌아 400에는 CORS 헤더가 없다(정상 브라우저 요청은 이런 경로를 보내지 않는다). 순서는 WebFilterConfig.
 * 헤더 · 쿼리 파라미터는 뒤 필터가 읽을 때 검사되므로(방화벽이 감싼 요청) 그때 거절돼도 400이다(Spring Security WebFilterChainProxy와 같다).
 */
@Slf4j
@RequiredArgsConstructor
class RequestFirewallFilter implements WebFilter {

    private final StrictServerWebExchangeFirewall firewall = new StrictServerWebExchangeFirewall();
    private final ProblemResponse problem;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return firewall.getFirewalledExchange(exchange)
            .flatMap(chain::filter)
            // 공개 도메인 스캐너가 흔히 보내므로 INFO. 거절 이유는 남기지 않는다: 헤더 값 · 호스트가 그대로 들어간다
            .onErrorResume(ServerExchangeRejectedException.class, rejected -> {
                log.info("방화벽이 요청을 거절했습니다. method={}", exchange.getRequest().getMethod());
                return problem.badRequest(exchange);
            });
    }
}
