package com.bbcc.kidly.boundedcontext.review.in.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.bbcc.kidly.boundedcontext.review.app.CommunityDeadLetterService;
import com.bbcc.kidly.boundedcontext.review.app.ReplicaHealthService;
import com.bbcc.kidly.boundedcontext.review.app.ReviewModerationAdminService;
import com.bbcc.kidly.boundedcontext.review.app.ReviewQueryService;
import com.bbcc.kidly.boundedcontext.review.in.mcp.CommunityMcpTools;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletRegistrationBean;

/**
 * 스위치 kidly.community.mcp.enabled: 꺼지면(기본) MCP 빈 · 서블릿 경로가 없고, 켜면 관리자 확인 필터와 함께 경로를 연다.
 */
class CommunityMcpConfigTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
        .withUserConfiguration(CommunityMcpConfig.class)
        .withBean(ReviewQueryService.class, () -> mock(ReviewQueryService.class))
        .withBean(CommunityDeadLetterService.class, () -> mock(CommunityDeadLetterService.class))
        .withBean(ReviewModerationAdminService.class, () -> mock(ReviewModerationAdminService.class))
        .withBean(ReplicaHealthService.class, () -> mock(ReplicaHealthService.class));

    @Test
    @DisplayName("기본(꺼짐)이면 도구 · 서블릿 · 필터가 없다(경로 없음)")
    void offByDefault() {
        runner.run(context -> {
            assertThat(context).doesNotHaveBean(CommunityMcpTools.class);
            assertThat(context).doesNotHaveBean(ServletRegistrationBean.class);
            assertThat(context).doesNotHaveBean(FilterRegistrationBean.class);
        });
    }

    @Test
    @DisplayName("켜면 /api/v1/admin/community/mcp에 서블릿과 관리자 확인 필터를 등록한다")
    void onRegistersEndpoint() {
        runner.withPropertyValues("kidly.community.mcp.enabled=true").run(context -> {
            assertThat(context).hasSingleBean(CommunityMcpTools.class);
            assertThat(context.getBean(ServletRegistrationBean.class).getUrlMappings())
                .containsExactly("/api/v1/admin/community/mcp");
            assertThat(context.getBean(FilterRegistrationBean.class).getUrlPatterns())
                .containsExactly("/api/v1/admin/community/mcp");
        });
    }

    @Test
    @DisplayName("결과 상한은 1~100(기본 50), 밖이면 키 이름이 나오는 오류로 기동에 실패한다")
    void maxResultsBounds() {
        runner.withPropertyValues("kidly.community.mcp.enabled=true").run(context ->
            assertThat(context.getBean(CommunityMcpProperties.class).maxResults()).isEqualTo(50));
        runner.withPropertyValues("kidly.community.mcp.enabled=true", "kidly.community.mcp.max-results=101")
            .run(context -> assertThat(context.getStartupFailure()).hasStackTraceContaining("kidly.community.mcp"));
        runner.withPropertyValues("kidly.community.mcp.enabled=true", "kidly.community.mcp.max-results=0")
            .run(context -> assertThat(context).hasFailed());
    }
}
