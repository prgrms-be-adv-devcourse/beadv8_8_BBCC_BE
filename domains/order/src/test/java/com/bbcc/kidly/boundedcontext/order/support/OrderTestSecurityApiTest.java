package com.bbcc.kidly.boundedcontext.order.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * 주문 기능 테스트 기반(#616): common 보안 설정 그대로, 주문 테스트가 만든 토큰(TestTokens)으로 로그인 · 권한이 동작한다.
 */
@FunctionalTest
class OrderTestSecurityApiTest {

    @Autowired
    private RestTestClient client;

    @Autowired
    private TestTokens tokens;

    @Test
    @DisplayName("구매자 토큰으로 내 주문 목록 200(빈 목록), 토큰이 없으면 401")
    void buyerTokenIsAccepted() {
        client.get().uri("/api/v1/orders")
            .headers(headers -> headers.setBearerAuth(tokens.buyer(1L)))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content.length()").isEqualTo(0);
        client.get().uri("/api/v1/orders")
            .exchange()
            .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("관리자 주문 API는 관리자 토큰이면 200, 구매자 토큰이면 403")
    void adminRoleIsChecked() {
        client.get().uri("/api/v1/admin/orders")
            .headers(headers -> headers.setBearerAuth(tokens.admin(99L)))
            .exchange()
            .expectStatus().isOk();
        client.get().uri("/api/v1/admin/orders")
            .headers(headers -> headers.setBearerAuth(tokens.buyer(1L)))
            .exchange()
            .expectStatus().isForbidden();
    }
}
