package com.bbcc.kidly.boundedcontext.review.in.config;

import com.bbcc.kidly.boundedcontext.review.app.CommunityDeadLetterService;
import com.bbcc.kidly.boundedcontext.review.app.ReplicaHealthService;
import com.bbcc.kidly.boundedcontext.review.app.ReviewModerationAdminService;
import com.bbcc.kidly.boundedcontext.review.app.ReviewQueryService;
import com.bbcc.kidly.boundedcontext.review.in.mcp.CommunityMcpAdminFilter;
import com.bbcc.kidly.boundedcontext.review.in.mcp.CommunityMcpSdkBinding;
import com.bbcc.kidly.boundedcontext.review.in.mcp.CommunityMcpTools;
import jakarta.servlet.http.HttpServlet;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * 운영 조회 MCP 창구(읽기 전용)를 kidly.community.mcp.enabled가 true일 때만 연다: 도구 정의 → SDK 전송 → 서블릿 등록
 * (/api/v1/admin/community/mcp, 관리자 확인 필터를 앞에 둔다). 꺼지면 이 빈들과 경로가 없다.
 */
@Configuration
@ConditionalOnProperty(name = "kidly.community.mcp.enabled", havingValue = "true")
@EnableConfigurationProperties(CommunityMcpProperties.class)
public class CommunityMcpConfig {

    static final String PATH = "/api/v1/admin/community/mcp";

    @Bean
    CommunityMcpTools communityMcpTools(ReviewQueryService reviewQueries, CommunityDeadLetterService deadLetters,
        ReviewModerationAdminService moderation, ReplicaHealthService replicaHealth,
        CommunityMcpProperties properties) {
        return CommunityMcpTools.of(reviewQueries, deadLetters, moderation, replicaHealth, properties.maxResults());
    }

    @Bean(destroyMethod = "close")
    CommunityMcpSdkBinding communityMcpSdkBinding(CommunityMcpTools communityMcpTools) {
        return new CommunityMcpSdkBinding(communityMcpTools, PATH);
    }

    @Bean
    ServletRegistrationBean<HttpServlet> communityMcpServlet(CommunityMcpSdkBinding communityMcpSdkBinding) {
        ServletRegistrationBean<HttpServlet> registration =
            new ServletRegistrationBean<>(communityMcpSdkBinding.servlet(), PATH);
        registration.setName("communityMcp");
        return registration;
    }

    @Bean
    FilterRegistrationBean<CommunityMcpAdminFilter> communityMcpAdminFilter() {
        FilterRegistrationBean<CommunityMcpAdminFilter> registration =
            new FilterRegistrationBean<>(new CommunityMcpAdminFilter());
        registration.addUrlPatterns(PATH);
        registration.setName("communityMcpAdminFilter");
        // 공용 요청 로그 필터(common RequestLoggingFilter, @Order(HIGHEST_PRECEDENCE)) 뒤에 돈다. 그래야 이 필터가 낸 401 · 413에도
        // 요청 추적 값(traceId)이 MDC · 응답 헤더(X-Request-Id)에 있다. 같거나 앞선 순서로 바꾸지 않는다(CommunityMcpApiTest)
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }
}
