package com.bbcc.kidly.boundedcontext.order.in.config;

import com.bbcc.kidly.boundedcontext.order.app.deadletter.OrderDeadLetterService;
import com.bbcc.kidly.boundedcontext.order.app.order.OrderEventFailureService;
import com.bbcc.kidly.boundedcontext.order.app.order.OrderQueryService;
import com.bbcc.kidly.boundedcontext.order.app.order.PaymentRecoveryService;
import com.bbcc.kidly.boundedcontext.order.app.outbox.OrderOutboxAdminService;
import com.bbcc.kidly.boundedcontext.order.app.stuck.StuckOrderService;
import com.bbcc.kidly.boundedcontext.order.in.mcp.OrderMcpServlet;
import com.bbcc.kidly.boundedcontext.order.in.mcp.OrderMcpTools;
import com.bbcc.kidly.boundedcontext.order.in.web.admin.AdminOrderController;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStatelessServerTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/**
 * 주문 운영 조회 MCP 창구(#925, 읽기 전용)를 서블릿으로 연다. 스위치(kidly.order.mcp.enabled)가 켜졌을 때만 뜬다.
 * MVC 밖이라 @LoginAdmin 대신 {@link OrderMcpServlet}이 X-Admin-Id를 직접 본다. 관리자만 통과시키는 것은 Gateway(/api/v1/admin/**)다.
 * 상태 없는 전송(요청마다 독립) + 동기 서버, 도구는 서블릿 스레드에서 바로 실행한다(immediateExecution).
 * JSON은 Boot가 관리하는 JsonMapper를 써서 시각 형식이 다른 API와 같다.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "kidly.order.mcp.enabled", havingValue = "true")
public class OrderMcpConfig {

    public static final String MCP_PATH = AdminOrderController.ADMIN_ORDERS_PATH + "/mcp";
    // 도구 인자만 받는 읽기 전용 창구라 요청 본문은 작다(SDK 기본 16MB 대신)
    private static final int MAX_REQUEST_BYTES = 64 * 1024;

    @Bean
    public OrderMcpTools orderMcpTools(StuckOrderService stuckOrderService, OrderQueryService orderQueries,
        OrderDeadLetterService deadLetterService, OrderEventFailureService failureService,
        PaymentRecoveryService recoveryService, OrderOutboxAdminService outboxService,
        OrderMcpProperties properties, JsonMapper jsonMapper) {
        return new OrderMcpTools(stuckOrderService, orderQueries, deadLetterService, failureService,
            recoveryService, outboxService, properties.maxResults(), jsonMapper);
    }

    @Bean
    public HttpServletStatelessServerTransport orderMcpTransport(JsonMapper jsonMapper) {
        return HttpServletStatelessServerTransport.builder()
            .jsonMapper(mcpJsonMapper(jsonMapper))
            .messageEndpoint(MCP_PATH)
            .maxRequestSize(MAX_REQUEST_BYTES)
            .build();
    }

    @Bean(destroyMethod = "close")
    public McpStatelessSyncServer orderMcpServer(HttpServletStatelessServerTransport orderMcpTransport,
        OrderMcpTools orderMcpTools, JsonMapper jsonMapper) {
        return McpServer.sync(orderMcpTransport)
            .serverInfo("kidly-order-ops", "1.0.0")
            .instructions("Kidly 주문 운영 상태를 읽기 전용으로 조회한다. 재처리 · 종료 같은 조작은 관리자 API로 사람이 한다.")
            .jsonMapper(mcpJsonMapper(jsonMapper))
            .capabilities(McpSchema.ServerCapabilities.builder().tools(false).build())
            .immediateExecution(true)
            .tools(orderMcpTools.specifications())
            .build();
    }

    @Bean
    public ServletRegistrationBean<OrderMcpServlet> orderMcpServletRegistration(
        HttpServletStatelessServerTransport orderMcpTransport, McpStatelessSyncServer orderMcpServer,
        JsonMapper jsonMapper) {
        ServletRegistrationBean<OrderMcpServlet> registration =
            new ServletRegistrationBean<>(new OrderMcpServlet(orderMcpTransport, jsonMapper), MCP_PATH);
        registration.setName("orderMcpServlet");
        return registration;
    }

    private static McpJsonMapper mcpJsonMapper(JsonMapper jsonMapper) {
        return new JacksonMcpJsonMapper(jsonMapper);
    }
}
