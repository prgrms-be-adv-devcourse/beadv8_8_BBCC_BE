package com.bbcc.kidly.boundedcontext.order.in.web.admin;

import com.bbcc.kidly.boundedcontext.support.FunctionalTest;
import com.bbcc.kidly.global.security.TestAdmins;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.client.RestTestClient;

@FunctionalTest
class AdminPurchaseConfirmationReplayApiTest {

    private static final String PATH = "/api/v1/admin/orders/purchase-confirmed-events";

    @Autowired
    private RestTestClient client;

    @Test
    @DisplayName("관리자가 빈 범위를 재발행하면 0건과 문자열 상한으로 완료 응답한다")
    void emptyBatch() {
        client.post().uri(PATH).headers(TestAdmins.of(99L)).exchange().expectStatus().isOk().expectBody()
            .jsonPath("$.published").isEqualTo(0).jsonPath("$.throughOrderItemId").isEqualTo("0")
            .jsonPath("$.hasNext").isEqualTo(false).jsonPath("$.nextAfterOrderItemId").isEmpty();
    }

    @Test
    @DisplayName("인증과 잘못된 범위·크기를 검증한다")
    void validatesRequests() {
        client.post().uri(PATH).exchange().expectStatus().isUnauthorized();
        client.post().uri(PATH + "?size=101").headers(TestAdmins.of(99L)).exchange().expectStatus().isBadRequest();
        client.post().uri(PATH + "?afterOrderItemId=-1").headers(TestAdmins.of(99L))
            .exchange().expectStatus().isBadRequest();
        client.post().uri(PATH + "?afterOrderItemId=2&throughOrderItemId=1").headers(TestAdmins.of(99L))
            .exchange().expectStatus().isBadRequest();
    }
}
