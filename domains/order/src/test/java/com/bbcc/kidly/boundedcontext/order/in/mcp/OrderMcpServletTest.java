package com.bbcc.kidly.boundedcontext.order.in.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/**
 * MCP 입구의 관리자 헤더 검사(#925). 공용 LoginAdmin 해석기와 같은 규칙: 숫자만 · 0보다 큼 · Long 범위.
 */
class OrderMcpServletTest {

    private final AtomicInteger passed = new AtomicInteger();
    private final OrderMcpServlet servlet = new OrderMcpServlet(new HttpServlet() {
        @Override
        protected void service(HttpServletRequest request, HttpServletResponse response) {
            passed.incrementAndGet();
            response.setStatus(200);
        }
    }, JsonMapper.shared());

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "abc", "0", "-1", "+1", " 1", "1.0", "99999999999999999999"})
    @DisplayName("X-Admin-Id가 없거나 잘못되면 401 ProblemDetail이고 전송으로 넘기지 않는다")
    void rejects(String adminId) throws Exception {
        MockHttpServletResponse response = send(adminId);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith("application/problem+json");
        assertThat(response.getContentAsString()).contains("\"status\":401");
        assertThat(passed).hasValue(0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "99"})
    @DisplayName("올바른 관리자 ID면 전송으로 넘긴다")
    void passes(String adminId) throws Exception {
        assertThat(send(adminId).getStatus()).isEqualTo(200);
        assertThat(passed).hasValue(1);
    }

    private MockHttpServletResponse send(String adminId) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/admin/orders/mcp");
        if (adminId != null) {
            request.addHeader("X-Admin-Id", adminId);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        servlet.service(request, response);
        return response;
    }
}
