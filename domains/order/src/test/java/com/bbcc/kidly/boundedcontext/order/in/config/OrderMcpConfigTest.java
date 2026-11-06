package com.bbcc.kidly.boundedcontext.order.in.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.bbcc.kidly.boundedcontext.order.app.deadletter.OrderDeadLetterService;
import com.bbcc.kidly.boundedcontext.order.app.order.OrderEventFailureService;
import com.bbcc.kidly.boundedcontext.order.app.order.OrderQueryService;
import com.bbcc.kidly.boundedcontext.order.app.order.PaymentRecoveryService;
import com.bbcc.kidly.boundedcontext.order.app.outbox.OrderOutboxAdminService;
import com.bbcc.kidly.boundedcontext.order.app.stuck.StuckOrderService;
import com.bbcc.kidly.boundedcontext.order.in.mcp.OrderMcpTools;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.json.JsonMapper;

/**
 * MCP 창구 스위치(#925): order.yml 기본은 꺼짐이라 서블릿 · 도구가 없고(경로 없음), ORDER_MCP_ENABLED=true일 때만 등록된다.
 */
class OrderMcpConfigTest {

    @Test
    @DisplayName("환경변수가 없으면 꺼짐: 서블릿 등록 · MCP 서버 · 도구가 없다. 상한 기본값은 50")
    void offByDefault() {
        appWithOrderYml(Map.of()).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(ServletRegistrationBean.class);
            assertThat(context).doesNotHaveBean(McpStatelessSyncServer.class);
            assertThat(context).doesNotHaveBean(OrderMcpTools.class);
            assertThat(context.getBean(OrderMcpProperties.class)).isEqualTo(new OrderMcpProperties(false, 50));
        });
    }

    @Test
    @DisplayName("ORDER_MCP_ENABLED=false면 꺼짐")
    void offWhenFalse() {
        appWithOrderYml(Map.of("ORDER_MCP_ENABLED", "false"))
            .run(context -> assertThat(context).doesNotHaveBean(ServletRegistrationBean.class));
    }

    @Test
    @DisplayName("ORDER_MCP_ENABLED=true면 /api/v1/admin/orders/mcp 하나에 서블릿이 등록되고 도구 8개")
    void onWhenTrue() {
        appWithOrderYml(Map.of("ORDER_MCP_ENABLED", "true")).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean("orderMcpServletRegistration", ServletRegistrationBean.class).getUrlMappings())
                .containsExactly("/api/v1/admin/orders/mcp");
            assertThat(context.getBean(McpStatelessSyncServer.class).listTools()).hasSize(8);
        });
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 101})
    @DisplayName("상한이 1~100 밖이면 키 이름이 나오는 바인딩 오류로 기동 실패")
    void invalidMaxResultsFails(int maxResults) {
        appWithOrderYml(Map.of("KIDLY_ORDER_MCP_MAXRESULTS", String.valueOf(maxResults)))
            .run(context -> assertThat(context).getFailure().hasStackTraceContaining("kidly.order.mcp"));
    }

    @EnableConfigurationProperties(OrderMcpProperties.class)
    static class McpOnly {
    }

    private static ApplicationContextRunner appWithOrderYml(Map<String, Object> environmentVariables) {
        return new ApplicationContextRunner()
            .withUserConfiguration(McpOnly.class, OrderMcpConfig.class)
            .withBean(JsonMapper.class, JsonMapper::shared)
            .withBean(StuckOrderService.class, () -> mock(StuckOrderService.class))
            .withBean(OrderQueryService.class, () -> mock(OrderQueryService.class))
            .withBean(OrderDeadLetterService.class, () -> mock(OrderDeadLetterService.class))
            .withBean(OrderEventFailureService.class, () -> mock(OrderEventFailureService.class))
            .withBean(PaymentRecoveryService.class, () -> mock(PaymentRecoveryService.class))
            .withBean(OrderOutboxAdminService.class, () -> mock(OrderOutboxAdminService.class))
            .withInitializer(context -> {
                try {
                    MutablePropertySources sources = context.getEnvironment().getPropertySources();
                    sources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
                    sources.replace(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                        new SystemEnvironmentPropertySource(
                            StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, environmentVariables));
                    new YamlPropertySourceLoader().load("order.yml", new ClassPathResource("order.yml"))
                        .forEach(sources::addLast);
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            });
    }
}
