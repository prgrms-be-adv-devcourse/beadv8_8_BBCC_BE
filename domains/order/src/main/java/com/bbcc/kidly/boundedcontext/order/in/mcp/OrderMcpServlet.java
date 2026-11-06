package com.bbcc.kidly.boundedcontext.order.in.mcp;

import com.bbcc.kidly.global.error.CommonErrorCode;
import com.bbcc.kidly.global.error.ProblemDetails;
import com.bbcc.kidly.shared.auth.PrincipalHeaders;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import tools.jackson.databind.json.JsonMapper;

/**
 * 주문 운영 조회 MCP 창구(#925)의 입구. MVC 밖 서블릿이라 @LoginAdmin을 쓰지 못해, 공용 LoginAdmin 해석기와 같은 규칙으로
 * X-Admin-Id를 직접 본다(숫자만 · 0보다 큼). 없거나 잘못되면 401 ProblemDetail로 거절하고 MCP 전송에 넘기지 않는다.
 */
public class OrderMcpServlet extends HttpServlet {

    private final HttpServlet transport;
    private final transient JsonMapper jsonMapper;

    public OrderMcpServlet(HttpServlet transport, JsonMapper jsonMapper) {
        this.transport = transport;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        transport.init(config);
    }

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException {
        boolean admin = PrincipalHeaders.parseId(request.getHeader(PrincipalHeaders.ADMIN_ID))
            .filter(id -> id > 0)
            .isPresent();
        if (!admin) {
            unauthorized(request, response);
            return;
        }
        transport.service(request, response);
    }

    @Override
    public void destroy() {
        transport.destroy();
        super.destroy();
    }

    private void unauthorized(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ProblemDetail problem = ProblemDetails.of(CommonErrorCode.UNAUTHORIZED, request.getRequestURI());
        response.setStatus(problem.getStatus());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write(jsonMapper.writeValueAsString(problem));
    }
}
