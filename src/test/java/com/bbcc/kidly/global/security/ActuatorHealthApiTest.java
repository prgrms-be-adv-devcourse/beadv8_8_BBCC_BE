package com.bbcc.kidly.global.security;

import com.bbcc.kidly.support.FunctionalTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.client.RestTestClient;

@FunctionalTest
class ActuatorHealthApiTest {

    @Autowired
    private RestTestClient client;

    @Test
    @DisplayName("헬스체크는 토큰 없이 호출할 수 있고 상태만 응답한다")
    void withoutToken() {
        client.get().uri("/actuator/health")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.status").isEqualTo("UP")
            .jsonPath("$.components").doesNotExist();
    }
}
