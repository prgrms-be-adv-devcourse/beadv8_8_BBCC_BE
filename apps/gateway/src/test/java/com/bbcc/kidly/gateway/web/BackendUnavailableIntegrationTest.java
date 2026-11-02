package com.bbcc.kidly.gateway.web;

import com.bbcc.kidly.gateway.support.GatewayTestSupport;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * 백엔드 연결이 실패하면(닫힌 포트) 503 ProblemDetail을 내는지 본다(#668). 다른 통합 테스트와 같은 컨텍스트(@IntegrationTest)를
 * 쓰면 RecordingBackendConfiguration이 app-uri를 가짜 백엔드 포트로 덮어써서 닫힌 포트를 만들 수 없다. Gateway 컨텍스트는 DB가
 * 없어 가벼우므로 이 테스트만 따로 띄운다.
 */
@Tag("integration")
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class BackendUnavailableIntegrationTest {

    @LocalServerPort
    private int port;

    private WebTestClient client;

    @DynamicPropertySource
    static void backendUris(DynamicPropertyRegistry registry) {
        var closedPort = closedPort();
        registry.add("kidly.gateway.app-uri", () -> "http://127.0.0.1:" + closedPort);
        registry.add("kidly.gateway.search-uri", () -> "http://127.0.0.1:" + closedPort);
    }

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    @DisplayName("백엔드 연결이 실패하면 503 ProblemDetail이다")
    void respondsServiceUnavailable() {
        var response = client.get().uri("/api/v1/members/me").exchange();

        GatewayTestSupport.expectProblem(response, HttpStatus.SERVICE_UNAVAILABLE, "서비스를 잠시 사용할 수 없습니다",
            "잠시 후 다시 시도해 주세요.", "/api/v1/members/me");
        response.expectHeader().exists("X-Request-Id");
    }

    // 요청 전에 닫아서 연결이 거절되는(ConnectException) 포트를 만든다
    private static int closedPort() {
        try (var socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
