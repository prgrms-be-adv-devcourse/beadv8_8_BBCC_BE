package com.bbcc.kidly.gateway;

import com.bbcc.kidly.gateway.support.GatewayTestSupport;
import com.bbcc.kidly.gateway.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Gateway가 DB 없이 뜨고 살아 있는지(liveness) 본다.
 * 전체 health 는 토큰 무효화 목록을 다 읽기 전까지 readiness 때문에 DOWN 이라(RevocationReplay) 시점에 따라 달라진다.
 */
@IntegrationTest
class GatewayApplicationIntegrationTest extends GatewayTestSupport {

    @Test
    @DisplayName("DB 없이 뜨고 liveness가 UP이다")
    void health() {
        client.get().uri("/actuator/health/liveness").exchange()
            .expectStatus().isOk()
            .expectBody().jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    @DisplayName("지원하지 않는 메서드는 405다(504가 아닌 ResponseStatusException은 바꾸지 않는다)")
    void unsupportedMethodIsMethodNotAllowed() {
        client.post().uri("/actuator/health").exchange()
            .expectStatus().isEqualTo(405);
    }
}
