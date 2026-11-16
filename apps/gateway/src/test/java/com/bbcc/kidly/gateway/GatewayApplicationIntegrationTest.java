package com.bbcc.kidly.gateway;

import com.bbcc.kidly.gateway.support.GatewayTestSupport;
import com.bbcc.kidly.gateway.support.IntegrationTest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.test.web.reactive.server.WebTestClient;

@IntegrationTest
class GatewayApplicationIntegrationTest extends GatewayTestSupport {

    @LocalManagementPort
    private int managementPort;

    @Test
    @DisplayName("공개 포트에서 actuator는 404다")
    void actuatorIsNotExposedOnPublicPort() {
        for (var path : List.of("/actuator/health", "/actuator/prometheus")) {
            expectNotFound(client.get().uri(path).exchange(), path);
        }
    }

    @Test
    @DisplayName("management 포트에서는 health가 UP이고 prometheus를 읽을 수 있다")
    void actuatorIsExposedOnManagementPort() {
        var management = WebTestClient.bindToServer().baseUrl("http://localhost:" + managementPort).build();
        management.get().uri("/actuator/health").exchange()
            .expectStatus().isOk().expectBody().jsonPath("$.status").isEqualTo("UP");
        management.get().uri("/actuator/health/liveness").exchange()
            .expectStatus().isOk().expectBody().jsonPath("$.status").isEqualTo("UP");
        management.get().uri("/actuator/prometheus").exchange().expectStatus().isOk();
    }
}
