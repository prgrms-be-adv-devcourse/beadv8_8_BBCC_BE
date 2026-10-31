package com.bbcc.kidly.gateway;

import com.bbcc.kidly.gateway.support.GatewayTestSupport;
import com.bbcc.kidly.gateway.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Gateway가 DB 없이 뜨고 health가 UP인지 본다.
 */
@IntegrationTest
class GatewayApplicationIntegrationTest extends GatewayTestSupport {

    @Test
    @DisplayName("DB 없이 뜨고 health가 UP이다")
    void health() {
        client.get().uri("/actuator/health").exchange()
            .expectStatus().isOk()
            .expectBody().jsonPath("$.status").isEqualTo("UP");
    }
}
