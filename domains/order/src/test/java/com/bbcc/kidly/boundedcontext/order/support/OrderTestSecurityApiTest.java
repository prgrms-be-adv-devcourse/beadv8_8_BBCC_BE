package com.bbcc.kidly.boundedcontext.order.support;

import com.bbcc.kidly.global.security.TestMembers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * 주문 기능 테스트 기반(#616): common 보안 설정 그대로, Gateway 헤더(TestMembers)로 로그인 · 권한이 동작한다.
 */
@FunctionalTest
class OrderTestSecurityApiTest {

    @Autowired
    private RestTestClient client;

    @Test
    @DisplayName("구매자로 내 주문 목록 200(빈 목록), 로그인하지 않으면 401")
    void buyerHeadersAreAccepted() {
        client.get().uri("/api/v1/orders")
            .headers(TestMembers.buyer(1L))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content.length()").isEqualTo(0);
        client.get().uri("/api/v1/orders")
            .exchange()
            .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("관리자 주문 API는 관리자면 200, 구매자면 403")
    void adminRoleIsChecked() {
        client.get().uri("/api/v1/admin/orders")
            .headers(TestMembers.admin(99L))
            .exchange()
            .expectStatus().isOk();
        client.get().uri("/api/v1/admin/orders")
            .headers(TestMembers.buyer(1L))
            .exchange()
            .expectStatus().isForbidden();
    }
}
