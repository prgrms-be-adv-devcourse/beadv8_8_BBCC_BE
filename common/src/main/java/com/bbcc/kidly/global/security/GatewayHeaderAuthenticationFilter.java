package com.bbcc.kidly.global.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * gateway 모드에서 API Gateway가 토큰을 검증하고 붙인 X-Member-* 헤더로 로그인 회원을 만든다. JWT(Authorization)는 보지 않는다.
 * 서비스가 Gateway 뒤에서만 열린다는 전제다(Gateway가 밖에서 온 X-Member-* 헤더를 지운다).
 * 회원 ID 헤더가 없으면 익명으로 넘긴다(공개 API는 통과, 나머지는 진입점이 401). 헤더 값이 잘못되면 401로 끝낸다.
 * 닉네임은 UTF-8 퍼센트 인코딩(공백은 %20)으로 온다. Gateway AccessTokenFilter의 인코딩 규칙과 같아야 한다.
 * 빈으로 등록하면 서블릿 필터로도 한 번 더 걸리므로 SecurityConfig에서 직접 만든다.
 */
class GatewayHeaderAuthenticationFilter extends OncePerRequestFilter {

    // 헤더 이름은 Gateway(apps/gateway AccessTokenFilter)와 같아야 한다
    static final String MEMBER_ID = "X-Member-Id";
    static final String MEMBER_ROLES = "X-Member-Roles";
    static final String MEMBER_NICKNAME = "X-Member-Nickname";

    private final SecurityContextHolderStrategy securityContextHolderStrategy =
        SecurityContextHolder.getContextHolderStrategy();
    // jwt 모드(BearerTokenAuthenticationFilter)처럼 요청 속성에도 저장해 ERROR · ASYNC 재진입 때 다시 읽는다
    private final SecurityContextRepository securityContextRepository = new RequestAttributeSecurityContextRepository();
    private final AuthenticationEntryPoint authenticationEntryPoint;

    GatewayHeaderAuthenticationFilter(AuthenticationEntryPoint authenticationEntryPoint) {
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        var memberId = request.getHeader(MEMBER_ID);
        if (memberId == null) {
            filterChain.doFilter(request, response);
            return;
        }
        AuthMember member;
        try {
            member = new AuthMember(Long.valueOf(memberId), nickname(request), roles(request));
        } catch (IllegalArgumentException e) {
            // 밖에서 온 헤더 값은 예외 메시지에 넣지 않는다
            securityContextHolderStrategy.clearContext();
            authenticationEntryPoint.commence(request, response, new BadCredentialsException("잘못된 회원 헤더입니다"));
            return;
        }
        var authorities = member.roles().stream()
            .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
            .toList();
        var context = securityContextHolderStrategy.createEmptyContext();
        context.setAuthentication(new PreAuthenticatedAuthenticationToken(member, null, authorities));
        securityContextHolderStrategy.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        filterChain.doFilter(request, response);
    }

    private static String nickname(HttpServletRequest request) {
        var nickname = request.getHeader(MEMBER_NICKNAME);
        return nickname == null ? null : URLDecoder.decode(nickname, StandardCharsets.UTF_8);
    }

    // 쉼표로 구분한 역할(BUYER,SELLER). 비어 있으면 역할이 없다
    private static List<Role> roles(HttpServletRequest request) {
        var roles = request.getHeader(MEMBER_ROLES);
        if (roles == null || roles.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(roles.split(",")).map(Role::valueOf).toList();
    }
}
