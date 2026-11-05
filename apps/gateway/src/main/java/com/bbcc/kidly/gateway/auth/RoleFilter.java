package com.bbcc.kidly.gateway.auth;

import com.bbcc.kidly.gateway.web.ProblemResponse;
import com.bbcc.kidly.shared.auth.MemberHeaders;
import com.bbcc.kidly.shared.auth.PrincipalHeaders;
import com.bbcc.kidly.shared.auth.PrincipalType;
import com.bbcc.kidly.shared.auth.Role;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import reactor.core.publisher.Mono;

/**
 * 관리자 경로는 관리자 주체, 판매자 경로는 SELLER 역할의 회원 주체만 넘긴다.
 * 주체가 없으면 401, 주체가 다르거나 역할이 없으면 403이다.
 * 그 밖의 경로는 그대로 넘긴다(로그인이 필요한지는 서비스가 정한다).
 * AccessTokenFilter가 밖에서 온 주체 헤더를 지운 뒤 토큰으로 주체 헤더를 붙인다.
 * /**는 0개 이상의 경로 조각이라 /api/v1/admin 자체도 맞고 /api/v1/administrator는 맞지 않는다.
 */
@Component
@RequiredArgsConstructor
class RoleFilter implements GlobalFilter, Ordered {

    private static final List<Rule> RULES = List.of(
        new Rule(PathPatternParser.defaultInstance.parse("/api/v1/child/**"), PrincipalType.CHILD, null),
        new Rule(PathPatternParser.defaultInstance.parse("/api/v1/admin/**"), PrincipalType.ADMIN, null),
        new Rule(PathPatternParser.defaultInstance.parse("/api/v1/seller/**"), PrincipalType.MEMBER, Role.SELLER));

    private final ProblemResponse problem;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        var path = exchange.getRequest().getPath().pathWithinApplication();
        var rule = RULES.stream()
            .filter(candidate -> candidate.pattern().matches(path))
            .findFirst();
        if (rule.isEmpty()) {
            return chain.filter(exchange);
        }
        var headers = exchange.getRequest().getHeaders();
        if (!hasAnyPrincipal(headers)) {
            return problem.unauthorized(exchange);
        }
        if (!rule.get().allows(headers)) {
            return problem.forbidden(exchange);
        }
        return chain.filter(exchange);
    }

    private static boolean hasAnyPrincipal(HttpHeaders headers) {
        return Arrays.stream(PrincipalType.values())
            .anyMatch(type -> headers.getFirst(PrincipalHeaders.idHeader(type)) != null);
    }

    @Override
    public int getOrder() {
        return AccessTokenFilter.ORDER + 1;
    }

    // role이 null이면 주체만 본다
    private record Rule(PathPattern pattern, PrincipalType principal, Role role) {

        boolean allows(HttpHeaders headers) {
            if (headers.getFirst(PrincipalHeaders.idHeader(principal)) == null) {
                return false;
            }
            return role == null
                || MemberHeaders.decodeRoles(headers.getFirst(MemberHeaders.MEMBER_ROLES)).contains(role.name());
        }
    }
}
