package com.bbcc.kidly.global.security;

import com.bbcc.kidly.global.filter.RequestLoggingFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 로그인 회원으로 인증된 요청이면 회원 ID를 MDC에 넣어 그 요청의 모든 로그에 붙인다. 익명 요청은 principal이 AuthMember가 아니라 넣지 않는다.
 * Security 필터 체인에서 인증 필터 바로 뒤에 둔다(SecurityConfig). MDC는 RequestLoggingFilter가 요청 끝 로그를 남긴 뒤 지운다.
 * 빈으로 등록하면 서블릿 필터로도 한 번 더 걸리므로 SecurityConfig에서 직접 만든다.
 */
class MemberIdMdcFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthMember member) {
            MDC.put(RequestLoggingFilter.MEMBER_ID_MDC_KEY, member.getName());
        }
        filterChain.doFilter(request, response);
    }
}
