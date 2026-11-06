package com.bbcc.kidly.boundedcontext.review.in.mcp;

import com.bbcc.kidly.boundedcontext.review.app.ReviewErrorCode;
import com.bbcc.kidly.global.error.CommonErrorCode;
import com.bbcc.kidly.global.error.ErrorCode;
import com.bbcc.kidly.global.error.ProblemDetails;
import com.bbcc.kidly.shared.auth.PrincipalHeaders;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

/**
 * MCP 창구 앞 필터(MVC 밖 서블릿이라 여기서 한다).
 * <ul>
 *   <li>관리자 확인: 공용 관리자 규칙과 같게 X-Admin-Id가 없거나 양의 정수가 아니면 401 ProblemDetail
 *       (Gateway가 관리자만 넘기지만 서비스도 한 번 더 본다)</li>
 *   <li>요청 크기 상한 64KB: 밝힌 길이가 넘거나, 길이 없이 보낸 본문을 읽어 보니 넘으면 413 ProblemDetail. 넘지 않으면 읽은 본문을
 *       그대로 다음(전송 서블릿)에 넘긴다</li>
 * </ul>
 */
public class CommunityMcpAdminFilter extends OncePerRequestFilter {

    static final int MAX_BODY_BYTES = 64 * 1024;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        boolean admin = PrincipalHeaders.parseId(request.getHeader(PrincipalHeaders.ADMIN_ID))
            .filter(id -> id > 0)
            .isPresent();
        if (!admin) {
            writeProblem(request, response, CommonErrorCode.UNAUTHORIZED);
            return;
        }
        if (request.getContentLengthLong() > MAX_BODY_BYTES) {
            writeProblem(request, response, ReviewErrorCode.MCP_REQUEST_TOO_LARGE);
            return;
        }
        byte[] body = readAtMost(request.getInputStream(), MAX_BODY_BYTES + 1);
        if (body.length > MAX_BODY_BYTES) {
            writeProblem(request, response, ReviewErrorCode.MCP_REQUEST_TOO_LARGE);
            return;
        }
        chain.doFilter(new ReadBodyRequest(request, body), response);
    }

    private static byte[] readAtMost(InputStream input, int limit) throws IOException {
        return input.readNBytes(limit);
    }

    private void writeProblem(HttpServletRequest request, HttpServletResponse response, ErrorCode code)
        throws IOException {
        ProblemDetail problem = ProblemDetails.of(code, request.getRequestURI());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", String.valueOf(problem.getType()));
        body.put("title", problem.getTitle());
        body.put("status", problem.getStatus());
        body.put("detail", problem.getDetail());
        body.put("instance", String.valueOf(problem.getInstance()));
        response.setStatus(problem.getStatus());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(jsonMapper.writeValueAsString(body));
    }

    /**
     * 이미 읽은(상한 안의) 본문을 다시 읽게 해 주는 요청.
     */
    private static final class ReadBodyRequest extends HttpServletRequestWrapper {

        private final byte[] body;

        ReadBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return input.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public int read() {
                    return input.read();
                }

                @Override
                public int read(byte[] buffer, int offset, int length) {
                    return input.read(buffer, offset, length);
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            String encoding = getCharacterEncoding();
            Charset charset = encoding == null ? StandardCharsets.UTF_8 : Charset.forName(encoding);
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }
    }
}
