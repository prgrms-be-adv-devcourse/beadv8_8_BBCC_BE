package com.bbcc.kidly;

import com.bbcc.kidly.support.FunctionalTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.client.RestTestClient;

@FunctionalTest
class NotificationServiceApplicationApiTest {

    @Autowired
    private RestTestClient client;

    @Test
    @DisplayName("health가 UP이다")
    void healthUp() {
        client.get().uri("/actuator/health")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    @DisplayName("다른 도메인 API는 없다(404)")
    void noOtherDomainApi() {
        client.get().uri("/api/v1/products")
            .exchange()
            .expectStatus().isNotFound();
        client.get().uri("/api/v1/orders")
            .exchange()
            .expectStatus().isNotFound();
    }
}
