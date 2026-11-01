package com.bbcc.kidly.gateway.auth;

import com.bbcc.kidly.gateway.web.ProblemResponse;
import com.bbcc.kidly.shared.auth.AccessTokenClaims;
import com.bbcc.kidly.shared.auth.MemberHeaders;
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
 * X-Member-Id · X-Member-Roles · X-Member-Nickname을 붙여 넘긴다. 토큰이 없거나 Bearer가 아니면 그대로 넘긴다(로그인이 필요한지는 서비스가 정한다).
 * 검증에 실패하면 401. Authorization은 지우지 않고 그대로 넘기지만 서비스는 보지 않는다.
 * 헤더 이름 · 역할 목록 · 닉네임 인코딩은 shared MemberHeaders를, 클레임 이름은 AccessTokenClaims를 따른다.
 * 닉네임 클레임이 없으면 닉네임 헤더를 붙이지 않는다.
 */
@Component
@RequiredArgsConstructor
class AccessTokenFilter implements GlobalFilter, Ordered {

    // 라우팅 필터보다 먼저 돈다
    static final int ORDER = Ordered.HIGHEST_PRECEDENCE;

    private static final String BEARER = "Bearer ";

    private final ReactiveJwtDecoder jwtDecoder;
    private final ProblemResponse problem;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        var request = exchange.getRequest().mutate()
            .headers(headers -> {
                headers.remove(MemberHeaders.MEMBER_ID);
                headers.remove(MemberHeaders.MEMBER_ROLES);
                headers.remove(MemberHeaders.MEMBER_NICKNAME);
            })
            .build();
        var authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        // Spring Security의 Bearer 토큰 규칙(DefaultBearerTokenResolver)처럼 대소문자를 가리지 않는다. 가리면 "bearer <무효화된 토큰>"이 검증 없이 지나간다
        if (authorization == null || !authorization.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
            return chain.filter(exchange.mutate().request(request).build());
        }
        return jwtDecoder.decode(authorization.substring(BEARER.length()))
            .map(jwt -> Optional.of(withMember(request, jwt)))
            .onErrorResume(JwtException.class, e -> Mono.just(Optional.empty()))
            .flatMap(authenticated -> authenticated
                .map(member -> chain.filter(exchange.mutate().request(member).build()))
                .orElseGet(() -> problem.unauthorized(exchange)));
    }

    private static ServerHttpRequest withMember(ServerHttpRequest request, Jwt jwt) {
        var roles = Optional.ofNullable(jwt.getClaimAsStringList(AccessTokenClaims.ROLES)).orElse(List.of());
        var nickname = jwt.getClaimAsString(AccessTokenClaims.NICKNAME);
        return request.mutate()
            .headers(headers -> {
                headers.set(MemberHeaders.MEMBER_ID, jwt.getSubject());
                headers.set(MemberHeaders.MEMBER_ROLES, MemberHeaders.encodeRoles(roles));
                if (nickname != null) {
                    headers.set(MemberHeaders.MEMBER_NICKNAME, MemberHeaders.encodeNickname(nickname));
                }
            })
            .build();
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
