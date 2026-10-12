package com.bbcc.kidly.global.security;

import com.bbcc.kidly.support.FunctionalTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

@FunctionalTest
class SecurityProblemApiTest {

    @Autowired
    private RestTestClient client;

    @Test
    @DisplayName("로그인하지 않고 보호된 API를 호출하면 401 ProblemDetail로 응답한다")
    void unauthorized() {
        client.get().uri("/api/v1/orders")
            .exchange()
            .expectStatus().isUnauthorized()
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.type").isEqualTo("about:blank")
            .jsonPath("$.title").isEqualTo("로그인이 필요합니다")
            .jsonPath("$.status").isEqualTo(401)
            .jsonPath("$.instance").isEqualTo("/api/v1/orders");
    }
}
