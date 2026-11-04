package com.bbcc.kidly;

import com.bbcc.kidly.support.FunctionalTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * 알림 서비스가 알림 도메인만으로 뜨는지 본다. 알림에는 HTTP API가 없어 다른 도메인 API도 없어야 한다.
 * 알림 도메인 자체의 동작은 도메인 테스트와 이 서비스의 통합 테스트가 본다.
 */
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
