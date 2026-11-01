package com.bbcc.kidly.gateway.auth;

import com.bbcc.kidly.gateway.web.ProblemResponse;
import com.bbcc.kidly.shared.auth.MemberHeaders;
import com.bbcc.kidly.shared.auth.Role;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import reactor.core.publisher.Mono;

/**
 * 판매자 · 관리자 API의 역할을 검사한다. 토큰이 없으면(X-Member-Id가 없으면) 401, 역할이 없으면 403이고 넘기지 않는다.
 * 그 밖의 경로는 그대로 넘긴다(로그인이 필요한지는 서비스가 정한다).
 * AccessTokenFilter 뒤에 돌므로 X-Member-* 헤더는 Gateway가 토큰에서 붙인 값뿐이다(밖에서 온 것은 지워졌다).
 * /**는 0개 이상의 경로 조각이라 /api/v1/admin 자체도 맞고 /api/v1/administrator는 맞지 않는다(서비스가 역할을 검사하던 규칙과 같다).
 */
@Component
@RequiredArgsConstructor
class RoleFilter implements GlobalFilter, Ordered {

    private static final List<RequiredRole> REQUIRED_ROLES = List.of(
        new RequiredRole(PathPatternParser.defaultInstance.parse("/api/v1/admin/**"), Role.ADMIN.name()),
        new RequiredRole(PathPatternParser.defaultInstance.parse("/api/v1/seller/**"), Role.SELLER.name()));

    private final ProblemResponse problem;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        var path = exchange.getRequest().getPath().pathWithinApplication();
        var required = REQUIRED_ROLES.stream()
            .filter(rule -> rule.pattern().matches(path))
            .map(RequiredRole::role)
            .findFirst();
        if (required.isEmpty()) {
            return chain.filter(exchange);
        }
        var headers = exchange.getRequest().getHeaders();
        if (headers.getFirst(MemberHeaders.MEMBER_ID) == null) {
            return problem.unauthorized(exchange);
        }
        var roles = MemberHeaders.decodeRoles(headers.getFirst(MemberHeaders.MEMBER_ROLES));
        if (!roles.contains(required.get())) {
            return problem.forbidden(exchange);
        }
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return AccessTokenFilter.ORDER + 1;
    }

    private record RequiredRole(PathPattern pattern, String role) {
    }
}
