package com.bbcc.kidly.boundedcontext.review.in.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * 전송 계약(SDK 바인딩): 도구 정의 목록이 그대로 SDK 도구 명세가 되고, 호출이 정의를 부르며, 결과 · 오류가 MCP 결과로 바뀐다.
 */
class CommunityMcpSdkBindingTest {

    private final CommunityMcpTools tools = new CommunityMcpTools(List.of(new EchoTool()));
    private final CommunityMcpSdkBinding binding = new CommunityMcpSdkBinding(tools, "/api/v1/admin/community/mcp");

    @Test
    @DisplayName("도구 정의의 이름 · 설명 · 입력 스키마가 그대로 tools/list 명세가 된다(읽기 전용 표시)")
    void listsDefinitions() {
        assertThat(binding.toolSpecifications()).singleElement().satisfies(spec -> {
            assertThat(spec.tool().name()).isEqualTo("echo");
            assertThat(spec.tool().description()).isEqualTo("값을 그대로 돌려준다");
            assertThat(spec.tool().inputSchema()).isEqualTo(new EchoTool().inputSchema());
            assertThat(spec.tool().annotations().readOnlyHint()).isTrue();
        });
        assertThat(binding.server().listTools()).extracting(tool -> tool.name()).containsExactly("echo");
    }

    @Test
    @DisplayName("tools/call은 정의를 불러 구조화 결과와 같은 JSON 글을 돌려준다")
    void callsDefinition() {
        CallToolResult result = call(Map.of("value", "abc"));

        assertThat(result.isError()).isFalse();
        assertThat(result.structuredContent()).isEqualTo(Map.of("value", "abc"));
        assertThat(((TextContent) result.content().getFirst()).text()).isEqualTo("{\"value\":\"abc\"}");
    }

    @Test
    @DisplayName("도구 오류는 isError 결과(정해진 문구)로 바뀐다")
    void errorsBecomeToolErrors() {
        CallToolResult result = call(Map.of());

        assertThat(result.isError()).isTrue();
        assertThat(((TextContent) result.content().getFirst()).text()).isEqualTo("value가 필요합니다");
    }

    @Test
    @DisplayName("서블릿으로 받은 tools/call은 그 요청 스레드에서 도구를 실행한다(요청 추적 값이 이어진다)")
    void runsOnServletThread() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/admin/community/mcp");
        request.setContentType("application/json");
        request.addHeader("Accept", "application/json, text/event-stream");
        request.setContent(("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\","
            + "\"params\":{\"name\":\"echo\",\"arguments\":{\"value\":\"abc\"}}}")
            .getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();

        binding.servlet().service(request, response);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString(StandardCharsets.UTF_8)).contains("abc");
        assertThat(EchoTool.LAST_THREAD.get()).isSameAs(Thread.currentThread());
    }

    private CallToolResult call(Map<String, Object> arguments) {
        SyncToolSpecification spec = binding.toolSpecifications().getFirst();
        return spec.callHandler().apply(McpTransportContext.EMPTY, new CallToolRequest("echo", arguments));
    }

    private static final class EchoTool implements CommunityMcpTool {

        static final AtomicReference<Thread> LAST_THREAD = new AtomicReference<>();

        @Override
        public String name() {
            return "echo";
        }

        @Override
        public String description() {
            return "값을 그대로 돌려준다";
        }

        @Override
        public Map<String, Object> inputSchema() {
            return Map.of("type", "object", "properties", Map.of("value", Map.of("type", "string")));
        }

        @Override
        public Map<String, Object> call(McpToolArguments arguments) {
            LAST_THREAD.set(Thread.currentThread());
            return Map.of("value", arguments.requiredText("value"));
        }
    }
}
