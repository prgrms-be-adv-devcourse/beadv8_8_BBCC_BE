package com.bbcc.kidly.gateway.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.web.server.header.CacheControlServerHttpHeadersWriter;
import org.springframework.security.web.server.header.CompositeServerHttpHeadersWriter;
import org.springframework.security.web.server.header.ContentTypeOptionsServerHttpHeadersWriter;
import org.springframework.security.web.server.header.HttpHeaderWriterWebFilter;
import org.springframework.security.web.server.header.StrictTransportSecurityServerHttpHeadersWriter;
import org.springframework.security.web.server.header.XFrameOptionsServerHttpHeadersWriter;
import org.springframework.security.web.server.header.XXssProtectionServerHttpHeadersWriter;
import org.springframework.web.server.WebFilter;

/**
 * 모든 요청이 맨 먼저 거치는 WebFilter 둘: 보안 헤더 → 방화벽 순서다(방화벽 400에도 보안 헤더가 붙는다).
 * WebFilter라 CORS · 라우팅 · GlobalFilter(토큰 · 역할 검사)보다 먼저 돈다.
 */
@Configuration(proxyBeanMethods = false)
class WebFilterConfig {

    /**
     * 모든 응답에 Spring Security 기본 보안 헤더를 붙인다(#638). 서비스에서 Spring Security를 지워 서비스가 붙이던 헤더를 Gateway가 대신 붙인다.
     * Cache-Control · Pragma · Expires(응답에 셋 중 하나라도 있거나 304면 셋 다 붙이지 않는다), X-Content-Type-Options: nosniff,
     * HSTS(요청이 https일 때만. native라 nginx의 X-Forwarded-Proto를 본다), X-Frame-Options: DENY, X-XSS-Protection: 0.
     * 백엔드 응답 헤더가 다 붙은 뒤(응답을 보내기 직전)에 쓰고, 이미 있는 헤더는 덮거나 더하지 않는다.
     * SecureHeaders 필터는 쓰지 않는다: 라우트를 탄 응답에만 붙고(방화벽 400 · actuator는 빠진다) Cache-Control이 없으며 기본값이 다르다.
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    WebFilter securityHeadersFilter() {
        return new HttpHeaderWriterWebFilter(new CompositeServerHttpHeadersWriter(
            new CacheControlServerHttpHeadersWriter(),
            new ContentTypeOptionsServerHttpHeadersWriter(),
            new StrictTransportSecurityServerHttpHeadersWriter(),
            new XFrameOptionsServerHttpHeadersWriter(),
            new XXssProtectionServerHttpHeadersWriter()));
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 1)
    WebFilter requestFirewallFilter(ProblemResponse problem) {
        return new RequestFirewallFilter(problem);
    }
}
