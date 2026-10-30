package com.bbcc.kidly.gateway.auth;

import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 모든 요청에서 밖에서 보낸 X-Member-* 헤더를 지우고, Bearer 토큰이 있으면 검증 · 무효화 확인 뒤
 * X-Member-Id · X-Member-Roles를 붙여 넘긴다. 토큰이 없거나 Bearer가 아니면 그대로 넘긴다(공개 API 판단은 서비스).
 * 검증에 실패하면 401. Authorization은 지우지 않는다: 서비스가 아직 JWT를 직접 검증한다(헤더 모드는 다음 이슈).
 */
@Component
@RequiredArgsConstructor
class AccessTokenFilter implements GlobalFilter, Ordered {

    static final String MEMBER_ID = "X-Member-Id";
    static final String MEMBER_ROLES = "X-Member-Roles";
    private static final String BEARER = "Bearer ";
    // JwtAccessTokenIssuer의 클레임 이름과 같아야 한다
    private static final String ROLES_CLAIM = "roles";

    private final ReactiveJwtDecoder jwtDecoder;
    private final UnauthorizedResponse unauthorized;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        var request = exchange.getRequest().mutate()
            .headers(headers -> {
                headers.remove(MEMBER_ID);
                headers.remove(MEMBER_ROLES);
            })
            .build();
        var authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        // 서비스(DefaultBearerTokenResolver)처럼 대소문자를 가리지 않는다. 가리면 "bearer <무효화된 토큰>"이 검증 없이 지나간다
        if (authorization == null || !authorization.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
            return chain.filter(exchange.mutate().request(request).build());
        }
        return jwtDecoder.decode(authorization.substring(BEARER.length()))
            .map(jwt -> Optional.of(withMember(request, jwt)))
            .onErrorResume(JwtException.class, e -> Mono.just(Optional.empty()))
            .flatMap(authenticated -> authenticated
                .map(member -> chain.filter(exchange.mutate().request(member).build()))
                .orElseGet(() -> unauthorized.write(exchange)));
    }

    private static ServerHttpRequest withMember(ServerHttpRequest request, Jwt jwt) {
        var roles = Optional.ofNullable(jwt.getClaimAsStringList(ROLES_CLAIM)).orElse(List.of());
        return request.mutate()
            .header(MEMBER_ID, jwt.getSubject())
            .header(MEMBER_ROLES, String.join(",", roles))
            .build();
    }

    // 라우팅 필터보다 먼저 돈다
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
