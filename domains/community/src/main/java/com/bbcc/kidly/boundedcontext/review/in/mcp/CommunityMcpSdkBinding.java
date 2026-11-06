package com.bbcc.kidly.boundedcontext.review.in.mcp;

import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStatelessServerTransport;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import io.modelcontextprotocol.spec.McpSchema.ToolAnnotations;
import jakarta.servlet.http.HttpServlet;
import java.io.IOException;
import java.util.List;
import tools.jackson.databind.json.JsonMapper;

/**
 * MCP 전송(SDK): 도구 정의 목록을 SDK 도구 명세로 바꾸고, 상태 없는 서블릿 전송 + 동기 서버를 만든다.
 * MCP SDK 타입은 이 파일에만 둔다. SDK 버전 · 전송 방식이 바뀌면 이 파일(과 in/config 등록)만 고친다.
 * 도구 인자 검사는 도구 정의가 하므로 SDK의 입력 스키마 검사는 끈다(틀린 인자는 도구 오류로 돌려준다).
 */
public class CommunityMcpSdkBinding implements AutoCloseable {

    static final String SERVER_NAME = "kidly-community-ops";
    static final String SERVER_VERSION = "1.0.0";

    private final CommunityMcpTools tools;
    private final McpJsonMapper jsonMapper = new JacksonMcpJsonMapper(JsonMapper.builder().build());
    private final List<SyncToolSpecification> specifications;
    private final HttpServletStatelessServerTransport transport;
    private final McpStatelessSyncServer server;

    public CommunityMcpSdkBinding(CommunityMcpTools tools, String endpoint) {
        this.tools = tools;
        this.specifications = tools.tools().stream().map(this::specification).toList();
        this.transport = HttpServletStatelessServerTransport.builder()
            .jsonMapper(jsonMapper)
            .messageEndpoint(endpoint)
            .build();
        this.server = McpServer.sync(transport)
            .jsonMapper(jsonMapper)
            .serverInfo(SERVER_NAME, SERVER_VERSION)
            .capabilities(ServerCapabilities.builder().tools(false).build())
            .validateToolInputs(false)
            // 도구를 요청을 받은 서블릿 스레드에서 실행한다(요청 추적 값 traceId가 이어지고, 조회가 짧아 따로 스레드가 필요 없다)
            .immediateExecution(true)
            .tools(specifications)
            .build();
    }

    public List<SyncToolSpecification> toolSpecifications() {
        return specifications;
    }

    public McpStatelessSyncServer server() {
        return server;
    }

    public HttpServlet servlet() {
        return transport;
    }

    @Override
    public void close() {
        server.close();
    }

    private SyncToolSpecification specification(CommunityMcpTool tool) {
        Tool definition = Tool.builder()
            .name(tool.name())
            .description(tool.description())
            .inputSchema(tool.inputSchema())
            .annotations(new ToolAnnotations(null, true, false, true, false, null))
            .build();
        return new SyncToolSpecification(definition,
            (context, request) -> result(tools.call(tool.name(), request.arguments())));
    }

    private CallToolResult result(ToolOutcome outcome) {
        if (outcome.error()) {
            return CallToolResult.builder().addTextContent(outcome.message()).isError(true).build();
        }
        try {
            return CallToolResult.builder()
                .structuredContent(outcome.structured())
                .addTextContent(jsonMapper.writeValueAsString(outcome.structured()))
                .isError(false)
                .build();
        } catch (IOException e) {
            return CallToolResult.builder().addTextContent("결과를 만들지 못했습니다").isError(true).build();
        }
    }
}
