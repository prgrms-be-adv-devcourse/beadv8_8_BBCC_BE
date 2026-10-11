package com.bbcc.kidly.global.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;

class SecurityProblemHandlerTest {

    private final SecurityProblemHandler handler = new SecurityProblemHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/orders");
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @Test
    @DisplayName("인증이 없으면 401 ProblemDetail로 응답한다")
    void unauthorized() throws Exception {
        handler.commence(request, response, new InsufficientAuthenticationException("no token"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(response.getContentAsString())
            .contains("\"type\":\"about:blank\"", "\"title\":\"로그인이 필요합니다\"", "\"status\":401",
                "\"instance\":\"/api/v1/orders\"")
            .doesNotContain("no token");
    }

    @Test
    @DisplayName("권한이 없으면 403 ProblemDetail로 응답한다")
    void forbidden() throws Exception {
        handler.handle(request, response, new AccessDeniedException("denied"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(response.getContentAsString()).contains("\"title\":\"권한이 없습니다\"", "\"status\":403");
    }
}
