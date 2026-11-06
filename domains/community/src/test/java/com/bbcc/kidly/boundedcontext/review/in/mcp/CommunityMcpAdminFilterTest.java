package com.bbcc.kidly.boundedcontext.review.in.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.review.app.ReviewErrorCode;
import jakarta.servlet.ServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.util.StreamUtils;

/**
 * MCP 창구 앞 필터: 관리자 헤더 확인 뒤 요청 본문 크기 상한(64KB). 넘으면 413 ProblemDetail이고 다음으로 넘기지 않는다.
 */
class CommunityMcpAdminFilterTest {

    private static final int LIMIT = 64 * 1024;

    private final CommunityMcpAdminFilter filter = new CommunityMcpAdminFilter();

    @Test
    @DisplayName("길이를 밝힌 요청이 64KB를 넘으면 413 ProblemDetail")
    void declaredLengthTooLarge() throws Exception {
        MockHttpServletResponse response = run(request(new byte[LIMIT + 1], true), new AtomicReference<>());

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentType()).startsWith("application/problem+json");
        assertThat(response.getContentAsString(StandardCharsets.UTF_8))
            .contains(ReviewErrorCode.MCP_REQUEST_TOO_LARGE.title()).contains("\"status\":413");
    }

    @Test
    @DisplayName("길이 없이 보낸 본문이 64KB를 넘어도 413")
    void undeclaredLengthTooLarge() throws Exception {
        AtomicReference<byte[]> passed = new AtomicReference<>();

        MockHttpServletResponse response = run(request(new byte[LIMIT + 1], false), passed);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(passed.get()).isNull();
    }

    @Test
    @DisplayName("64KB까지는 넘기고, 길이 없이 보낸 본문도 그대로 다음이 읽는다")
    void withinLimitPasses() throws Exception {
        byte[] body = "{\"jsonrpc\":\"2.0\"}".getBytes(StandardCharsets.UTF_8);
        AtomicReference<byte[]> passed = new AtomicReference<>();

        assertThat(run(request(body, false), passed).getStatus()).isEqualTo(200);
        assertThat(passed.get()).isEqualTo(body);
        AtomicReference<byte[]> exact = new AtomicReference<>();
        assertThat(run(request(new byte[LIMIT], true), exact).getStatus()).isEqualTo(200);
        assertThat(exact.get()).hasSize(LIMIT);
    }

    @Test
    @DisplayName("관리자 헤더가 없으면 크기와 상관없이 먼저 401")
    void adminFirst() throws Exception {
        MockHttpServletRequest request = request(new byte[LIMIT + 1], true);
        request.removeHeader("X-Admin-Id");

        assertThat(run(request, new AtomicReference<>()).getStatus()).isEqualTo(401);
    }

    private MockHttpServletResponse run(MockHttpServletRequest request, AtomicReference<byte[]> passed)
        throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain() {
            @Override
            public void doFilter(ServletRequest next, jakarta.servlet.ServletResponse nextResponse)
                throws java.io.IOException {
                passed.set(StreamUtils.copyToByteArray(next.getInputStream()));
            }
        });
        return response;
    }

    private static MockHttpServletRequest request(byte[] body, boolean declareLength) {
        MockHttpServletRequest request = declareLength
            ? new MockHttpServletRequest("POST", "/api/v1/admin/community/mcp")
            : new MockHttpServletRequest("POST", "/api/v1/admin/community/mcp") {
                @Override
                public int getContentLength() {
                    return -1;
                }

                @Override
                public long getContentLengthLong() {
                    return -1;
                }
            };
        request.addHeader("X-Admin-Id", "99");
        request.setContentType("application/json");
        request.setContent(body);
        return request;
    }
}
