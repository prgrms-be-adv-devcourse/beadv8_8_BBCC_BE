package com.bbcc.kidly.global.security;

import com.bbcc.kidly.global.filter.RequestLoggingFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 토큰으로 인증된 요청이면 회원 ID(토큰 subject)를 MDC에 넣어 그 요청의 모든 로그에 붙인다.
 * Security 필터 체인에서 토큰 검증 바로 뒤에 둔다(SecurityConfig). MDC는 RequestLoggingFilter가 요청 끝 로그를 남긴 뒤 지운다.
 * 빈으로 등록하면 서블릿 필터로도 한 번 더 걸리므로 SecurityConfig에서 직접 만든다.
 */
class MemberIdMdcFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken authentication) {
            MDC.put(RequestLoggingFilter.MEMBER_ID_MDC_KEY, authentication.getName());
        }
        filterChain.doFilter(request, response);
    }
}
