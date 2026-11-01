package com.bbcc.kidly.gateway.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.gateway.support.GatewayTestSupport;
import com.bbcc.kidly.gateway.support.IntegrationTest;
import com.bbcc.kidly.gateway.support.RecordingBackend;
import com.bbcc.kidly.gateway.support.TestTokens;
import com.bbcc.kidly.shared.auth.MemberHeaders;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.FieldSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

/**
 * 정규화되지 않거나 위험한 경로가 역할 검사 · /internal/** 차단을 비껴가지 못하게 400으로 막는지 본다(#638).
 * 경로는 URI.create로 보내 WebTestClient가 다시 인코딩하지 않게 한다(그 바이트 그대로 나가는지는 sendsRawPathAsIs가 본다).
 */
@IntegrationTest
class RequestFirewallIntegrationTest extends GatewayTestSupport {

    // 백엔드가 정규화 · 디코딩 · 매트릭스 제거를 하면 /api/v1/admin/** · /internal/**로 읽힐 수 있는 경로
    private static final List<String> BYPASS_PATHS = List.of(
        "/api/v1/products/../admin/members",
        "/api/v1/./admin/members",
        "/api/v1/products/%2e%2e/admin/members",
        "/api/v1/products/%2E%2E/admin/members",
        "/api/v1/%2e/admin/members",
        "/api/v1//admin/members",
        "//api/v1/admin/members",
        "/api/v1/products/..%2fadmin/members",
        "/api/v1%2fadmin/members",
        "/api/v1/products/..%5cadmin/members",
        "/api/v1/products/%252e%252e/admin/members",
        "/api/v1/admin;x=1/members",
        "/api/v1/products/..;/admin/members",
        "/api/v1/products/../../../internal/members/1",
        "/api/%2e%2e/%2e%2e/internal/members/1",
        "//internal/members/1",
        "/api/..%2finternal/members/1",
        "/internal;x=1/members/1");

    @ParameterizedTest
    @FieldSource("BYPASS_PATHS")
    @DisplayName("정규화되지 않거나 위험한 경로는 400이고 넘기지 않는다")
    void rejectsBypassPaths(String path) {
        expectBadRequest(client.get().uri(raw(path)).exchange(), path);
    }

    // 위 경로가 클라이언트에서 바뀌지 않고 그대로 나가는지(그래서 400이 Gateway 판단인지) 원시 소켓으로 요청 줄을 받아 본다.
    // 가짜 백엔드(JDK HttpServer)는 //로 시작하는 경로에 스스로 400을 내므로 쓰지 않는다
    @ParameterizedTest
    @FieldSource("BYPASS_PATHS")
    @DisplayName("테스트 클라이언트는 경로를 바꾸지 않고 그대로 보낸다")
    void sendsRawPathAsIs(String path) throws Exception {
        try (var server = new ServerSocket(0)) {
            var requestLine = CompletableFuture.supplyAsync(() -> readRequestLine(server));

            client.get().uri(URI.create("http://127.0.0.1:" + server.getLocalPort() + path)).exchange()
                .expectStatus().isOk();

            assertThat(requestLine.get(5, TimeUnit.SECONDS)).isEqualTo("GET " + path + " HTTP/1.1");
        }
    }

    @Test
    @DisplayName("허용하지 않은 메서드(TRACE)는 400이고 넘기지 않는다")
    void rejectsTrace() {
        client.method(HttpMethod.TRACE).uri("/api/v1/products").exchange()
            .expectStatus().isBadRequest();

        assertThat(backend.lastRequest()).isEmpty();
    }

    // 헤더는 뒤 필터가 읽을 때 검사된다. AccessTokenFilter가 Authorization을 읽다가 거절된다
    @Test
    @DisplayName("제어 문자가 든 헤더를 읽으면 400이고 넘기지 않는다")
    void rejectsControlCharacterInHeader() {
        client.get().uri("/api/v1/products/1")
            .header(HttpHeaders.AUTHORIZATION, "Bearer \u0085")
            .exchange()
            .expectStatus().isBadRequest();

        assertThat(backend.lastRequest()).isEmpty();
    }

    // 경로 매칭(PathPattern)은 퍼센트 인코딩을 풀어서 보므로 방화벽이 막지 않는 인코딩(%61 = a, %69 = i)도 검사를 비껴가지 못한다
    @Test
    @DisplayName("글자를 퍼센트 인코딩한 관리자 경로도 역할을 검사한다(401)")
    void checksRoleOnEncodedAdminPath() {
        client.get().uri(raw("/api/v1/%61dmin/members")).exchange()
            .expectStatus().isUnauthorized();

        assertThat(backend.lastRequest()).isEmpty();
    }

    @Test
    @DisplayName("글자를 퍼센트 인코딩한 내부 API 경로도 404이고 넘기지 않는다")
    void blocksEncodedInternalPath() {
        client.get().uri(raw("/%69nternal/members/1")).exchange()
            .expectStatus().isNotFound();

        assertThat(backend.lastRequest()).isEmpty();
    }

    @Test
    @DisplayName("한글 · 공백 · 퍼센트 인코딩된 값이 든 쿼리는 그대로 넘긴다")
    void passesEncodedQuery() {
        client.get().uri(uri -> uri.path("/api/v1/products/1")
                .queryParam("keyword", "유아 원피스")
                .queryParam("next", "/api/v1/admin/../x;y")
                .queryParam("rate", "10%")
                .build())
            .exchange()
            .expectStatus().isOk();

        assertThat(backend.lastRequest()).hasValueSatisfying(request -> {
            assertThat(request.uri().getPath()).isEqualTo("/api/v1/products/1");
            assertThat(request.uri().getQuery())
                .isEqualTo("keyword=유아 원피스&next=/api/v1/admin/../x;y&rate=10%");
        });
    }

    @Test
    @DisplayName("한글이 든 경로(퍼센트 인코딩)는 그대로 넘긴다")
    void passesKoreanPath() {
        client.get().uri("/api/v1/categories/{name}", "유아 신발").exchange()
            .expectStatus().isOk();

        assertThat(backend.lastRequest()).hasValueSatisfying(
            request -> assertThat(request.uri().getPath()).isEqualTo("/api/v1/categories/유아 신발"));
    }

    @Test
    @DisplayName("퍼센트 인코딩한 헤더 값과 Gateway가 붙인 닉네임 헤더는 그대로 넘긴다")
    void passesPercentEncodedHeaders() {
        var nickname = "키들리 엄마+아빠 100%";
        client.get().uri("/api/v1/members/me")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.issueWithNickname(7L, nickname))
            .header("X-Client-Note", "%ED%82%A4%EB%93%A4%EB%A6%AC%20100%25")
            .exchange()
            .expectStatus().isOk();

        assertThat(backend.lastRequest()).hasValueSatisfying(request -> {
            assertThat(request.header(MemberHeaders.MEMBER_NICKNAME)).singleElement()
                .satisfies(value -> assertThat(URLDecoder.decode(value, StandardCharsets.UTF_8)).isEqualTo(nickname));
            assertThat(request.header("X-Client-Note")).containsExactly("%ED%82%A4%EB%93%A4%EB%A6%AC%20100%25");
        });
    }

    @Test
    @DisplayName("프리플라이트(OPTIONS)는 막지 않고 Gateway가 답한다")
    void passesPreflight() {
        client.options().uri("/api/v1/orders/1")
            .header(HttpHeaders.ORIGIN, RecordingBackend.ALLOWED_ORIGIN)
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.PATCH.name())
            .exchange()
            .expectStatus().isOk()
            .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, RecordingBackend.ALLOWED_ORIGIN);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PATCH", "DELETE", "PUT"})
    @DisplayName("PATCH · DELETE · PUT은 그대로 넘긴다")
    void passesWriteMethods(String method) {
        client.method(HttpMethod.valueOf(method)).uri("/api/v1/orders/1").exchange()
            .expectStatus().isOk();

        assertThat(backend.lastRequest()).hasValueSatisfying(
            request -> assertThat(request.method()).isEqualTo(method));
    }

    @Test
    @DisplayName("결제 웹훅은 그대로 넘긴다")
    void passesPaymentWebhook() {
        client.post().uri("/api/v1/webhooks/toss")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"eventType\":\"PAYMENT_STATUS_CHANGED\"}")
            .exchange()
            .expectStatus().isOk();

        assertThat(backend.lastRequest()).hasValueSatisfying(
            request -> assertThat(request.uri().getPath()).isEqualTo("/api/v1/webhooks/toss"));
    }

    // 요청 줄 하나를 읽고 200으로 답한다
    private static String readRequestLine(ServerSocket server) {
        try (var socket = server.accept()) {
            var reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
            var line = reader.readLine();
            var response = "HTTP/1.1 200 OK\r\nContent-Length: 0\r\nConnection: close\r\n\r\n";
            socket.getOutputStream().write(response.getBytes(StandardCharsets.US_ASCII));
            return line;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
