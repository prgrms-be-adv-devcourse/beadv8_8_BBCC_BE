package com.bbcc.kidly.gateway;

import com.bbcc.kidly.gateway.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Gateway가 DB 없이 뜨고 health가 UP인지 본다.
 */
@IntegrationTest
class GatewayApplicationIntegrationTest {

    @LocalServerPort
    private int port;

    @Test
    @DisplayName("DB 없이 뜨고 health가 UP이다")
    void health() {
        WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build()
            .get().uri("/actuator/health").exchange()
            .expectStatus().isOk()
            .expectBody().jsonPath("$.status").isEqualTo("UP");
    }
}
