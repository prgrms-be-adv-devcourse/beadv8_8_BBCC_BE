package com.bbcc.kidly.gateway.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec;

/**
 * 통합 테스트(@IntegrationTest)가 상속한다: 실제 포트로 뜬 Gateway에 보내는 client와 가짜 백엔드(kidly-app)를 테스트마다 새로 준비한다.
 * Gateway가 막은 응답은 expectBadRequest · expectUnauthorized · expectForbidden으로 본다.
 */
public abstract class GatewayTestSupport {

    @LocalServerPort
    private int port;

    @Autowired
    protected RecordingBackend backend;

    protected WebTestClient client;

    @BeforeEach
    void setUpGatewayClient() {
        backend.reset();
        client = WebTestClient.bindToServer().baseUrl(baseUrl()).build();
    }

    // client가 경로를 다시 인코딩하지 않게 그대로 보낼 때 쓴다
    protected URI raw(String path) {
        return URI.create(baseUrl() + path);
    }

    protected void expectBadRequest(ResponseSpec response, String instance) {
        expectRejected(response, HttpStatus.BAD_REQUEST, "잘못된 요청입니다", "요청 값을 확인해 주세요.", instance);
    }

    protected void expectUnauthorized(ResponseSpec response, String instance) {
        expectRejected(response, HttpStatus.UNAUTHORIZED, "로그인이 필요합니다", "로그인 후 다시 시도해 주세요.", instance);
    }

    protected void expectForbidden(ResponseSpec response, String instance) {
        expectRejected(response, HttpStatus.FORBIDDEN, "권한이 없습니다", "이 요청을 처리할 권한이 없습니다.", instance);
    }

    // 서비스의 400 · 401 · 403(common CommonErrorCode, 5필드 ProblemDetails)과 같은 본문이고 백엔드로 넘기지 않았다
    private void expectRejected(ResponseSpec response, HttpStatus status, String title, String detail,
        String instance) {
        response.expectStatus().isEqualTo(status)
            .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.type").isEqualTo("about:blank")
            .jsonPath("$.title").isEqualTo(title)
            .jsonPath("$.status").isEqualTo(status.value())
            .jsonPath("$.detail").isEqualTo(detail)
            .jsonPath("$.instance").isEqualTo(instance);
        assertThat(backend.lastRequest()).isEmpty();
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }
}
