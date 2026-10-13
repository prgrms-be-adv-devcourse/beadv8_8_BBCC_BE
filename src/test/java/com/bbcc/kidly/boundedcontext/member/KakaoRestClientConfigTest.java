package com.bbcc.kidly.boundedcontext.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.member.out.KakaoRestClientConfig;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;

class KakaoRestClientConfigTest {

    private static final String KAKAO_ERROR = "{\"error\":\"invalid_client\",\"error_code\":\"KOE004\"}";

    private HttpServer server;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/oauth/token", exchange -> {
            var body = KAKAO_ERROR.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", MediaType.APPLICATION_JSON_VALUE);
            exchange.sendResponseHeaders(401, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    // 카카오 오류 코드(KOE...)가 로그에 남아야 설정 문제를 찾을 수 있다
    @Test
    @DisplayName("POST에 401이 와도 응답 본문을 버리지 않는다")
    void keepsBodyOfUnauthorized() {
        var restClient = new KakaoRestClientConfig().kakaoRestClient();

        assertThatThrownBy(() -> restClient.post()
            .uri("http://localhost:" + server.getAddress().getPort() + "/oauth/token")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body("code=auth-code")
            .retrieve()
            .toBodilessEntity())
            .isInstanceOfSatisfying(HttpClientErrorException.Unauthorized.class,
                e -> assertThat(e.getResponseBodyAsString()).isEqualTo(KAKAO_ERROR));
    }
}
