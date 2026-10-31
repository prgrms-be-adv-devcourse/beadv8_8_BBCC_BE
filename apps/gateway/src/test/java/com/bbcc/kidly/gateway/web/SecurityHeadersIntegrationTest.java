package com.bbcc.kidly.gateway.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.gateway.support.GatewayTestSupport;
import com.bbcc.kidly.gateway.support.IntegrationTest;
import com.bbcc.kidly.gateway.support.TestTokens;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec;

/**
 * 서비스가 Spring Security로 붙이던 보안 헤더를 Gateway가 모든 응답에 한 번씩 붙이는지 본다(#638).
 */
@IntegrationTest
class SecurityHeadersIntegrationTest extends GatewayTestSupport {

    private static final String CACHE_CONTROL = "no-cache, no-store, max-age=0, must-revalidate";
    private static final String HSTS = "Strict-Transport-Security";

    @Test
    @DisplayName("백엔드 응답에 보안 헤더를 붙인다(http면 HSTS는 없다)")
    void addsHeadersToBackendResponse() {
        var response = client.get().uri("/api/v1/products").exchange()
            .expectStatus().isOk();

        expectSecurityHeaders(response);
        response.expectHeader().doesNotExist(HSTS);
    }

    @Test
    @DisplayName("백엔드가 Cache-Control을 정하면 그대로 두고 Pragma · Expires도 붙이지 않는다")
    void keepsBackendCacheControl() {
        backend.addResponseHeader(HttpHeaders.CACHE_CONTROL, "no-store");

        client.post().uri("/api/v1/auth/login").exchange()
            .expectStatus().isOk()
            .expectHeader().values(HttpHeaders.CACHE_CONTROL, values -> assertThat(values).containsExactly("no-store"))
            .expectHeader().doesNotExist(HttpHeaders.PRAGMA)
            .expectHeader().doesNotExist(HttpHeaders.EXPIRES)
            .expectHeader().values("X-Content-Type-Options", values -> assertThat(values).containsExactly("nosniff"));
    }

    @Test
    @DisplayName("백엔드가 같은 헤더를 붙여도 하나만 남는다")
    void doesNotDuplicateBackendHeaders() {
        backend.addResponseHeader("X-Content-Type-Options", "nosniff");
        backend.addResponseHeader("X-Frame-Options", "DENY");

        expectSecurityHeaders(client.get().uri("/api/v1/products/1").exchange().expectStatus().isOk());
    }

    @Test
    @DisplayName("nginx가 https로 받은 요청에는 HSTS를 붙인다")
    void addsHstsToHttpsRequest() {
        client.get().uri("/api/v1/products")
            .header("X-Forwarded-Proto", "https")
            .exchange()
            .expectStatus().isOk()
            .expectHeader().values(HSTS, values -> assertThat(values)
                .containsExactly("max-age=31536000 ; includeSubDomains"));
    }

    @Test
    @DisplayName("Gateway가 만든 401에도 붙인다")
    void addsHeadersToUnauthorized() {
        expectSecurityHeaders(client.get().uri("/api/v1/products")
            .header(HttpHeaders.AUTHORIZATION, "Bearer abc")
            .exchange()
            .expectStatus().isUnauthorized());
    }

    @Test
    @DisplayName("Gateway가 만든 403에도 붙인다")
    void addsHeadersToForbidden() {
        expectSecurityHeaders(client.get().uri("/api/v1/admin/members")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.issueWithNickname(7L, "구매자"))
            .exchange()
            .expectStatus().isForbidden());
    }

    @Test
    @DisplayName("방화벽이 만든 400에도 붙인다")
    void addsHeadersToFirewallBadRequest() {
        expectSecurityHeaders(client.get().uri(raw("/api/v1/../admin/members"))
            .exchange()
            .expectStatus().isBadRequest());
    }

    @Test
    @DisplayName("내부 API 404에도 붙인다")
    void addsHeadersToNotFound() {
        expectSecurityHeaders(client.get().uri("/internal/members/1").exchange().expectStatus().isNotFound());
    }

    @Test
    @DisplayName("actuator 응답에도 붙인다")
    void addsHeadersToActuator() {
        expectSecurityHeaders(client.get().uri("/actuator/health").exchange().expectStatus().isOk());
    }

    private static void expectSecurityHeaders(ResponseSpec response) {
        response
            .expectHeader().values(HttpHeaders.CACHE_CONTROL,
                values -> assertThat(values).containsExactly(CACHE_CONTROL))
            .expectHeader().values(HttpHeaders.PRAGMA, values -> assertThat(values).containsExactly("no-cache"))
            .expectHeader().values(HttpHeaders.EXPIRES, values -> assertThat(values).containsExactly("0"))
            .expectHeader().values("X-Content-Type-Options", values -> assertThat(values).containsExactly("nosniff"))
            .expectHeader().values("X-Frame-Options", values -> assertThat(values).containsExactly("DENY"))
            .expectHeader().values("X-XSS-Protection", values -> assertThat(values).containsExactly("0"));
    }
}
