package com.bbcc.kidly.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.support.FunctionalTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.client.RestTestClient;

@FunctionalTest
class ActuatorPrometheusApiTest {

    @Autowired
    private RestTestClient client;

    @Test
    @DisplayName("Prometheus 메트릭은 토큰 없이 호출할 수 있고 HTTP · JVM · 로그 지표를 담는다")
    void withoutToken() {
        // 요청 지표(http_server_requests)가 생기도록 먼저 한 번 부른다
        client.get().uri("/actuator/health").exchange().expectStatus().isOk();

        var body = client.get().uri("/actuator/prometheus")
            .exchange()
            .expectStatus().isOk()
            .expectBody(String.class)
            .returnResult()
            .getResponseBody();

        assertThat(body)
            .contains("http_server_requests_seconds_count")
            .contains("jvm_memory_used_bytes")
            .contains("logback_events_total")
            .contains("hikaricp_connections_active");
    }
}
