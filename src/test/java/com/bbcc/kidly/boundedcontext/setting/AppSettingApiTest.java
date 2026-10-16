package com.bbcc.kidly.boundedcontext.setting;

import com.bbcc.kidly.support.FunctionalTest;
import com.bbcc.kidly.support.TestTokens;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

@FunctionalTest
class AppSettingApiTest {

    private static final String PATH = "/api/v1/settings";
    private static final String ADMIN_PATH = "/api/v1/admin/settings";
    private static final Long ADMIN_ID = 3L;

    @Autowired
    private RestTestClient client;

    @Autowired
    private TestTokens tokens;

    @Test
    @DisplayName("로그인하지 않아도 설정을 조회한다. 바꾼 적이 없으면 목 데이터가 켜져 있다")
    void getDefaults() {
        client.get().uri(PATH)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.useMock").isEqualTo(true)
            .jsonPath("$.demoRoleSwitch").doesNotExist();
    }

    @Test
    @DisplayName("관리자가 바꾸면 바뀐 설정을 돌려주고, 조회에도 반영된다")
    void update() {
        client.put().uri(ADMIN_PATH)
            .headers(headers -> headers.setBearerAuth(tokens.admin(ADMIN_ID)))
            .contentType(MediaType.APPLICATION_JSON)
            .body("""
                {"useMock": false}
                """)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.useMock").isEqualTo(false);

        client.get().uri(PATH)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.useMock").isEqualTo(false);
    }

    @Test
    @DisplayName("값이 빠지면 400")
    void missingValue() {
        client.put().uri(ADMIN_PATH)
            .headers(headers -> headers.setBearerAuth(tokens.admin(ADMIN_ID)))
            .contentType(MediaType.APPLICATION_JSON)
            .body("""
                {}
                """)
            .exchange()
            .expectStatus().isBadRequest()
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    @DisplayName("로그인하지 않으면 바꿀 수 없다. 401이다")
    void updateWithoutToken() {
        client.put().uri(ADMIN_PATH)
            .contentType(MediaType.APPLICATION_JSON)
            .body("""
                {"useMock": false}
                """)
            .exchange()
            .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("관리자가 아니면 바꿀 수 없다. 403이다")
    void updateForbiddenForBuyer() {
        client.put().uri(ADMIN_PATH)
            .headers(headers -> headers.setBearerAuth(tokens.buyer(1L)))
            .contentType(MediaType.APPLICATION_JSON)
            .body("""
                {"useMock": false}
                """)
            .exchange()
            .expectStatus().isForbidden();
    }
}
