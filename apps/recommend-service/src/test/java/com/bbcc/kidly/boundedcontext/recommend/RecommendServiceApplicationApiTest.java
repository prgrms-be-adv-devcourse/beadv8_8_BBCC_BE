package com.bbcc.kidly.boundedcontext.recommend;

import com.bbcc.kidly.support.FunctionalTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * 추천 서비스가 추천 도메인만으로 뜨고, 추천 API 두 개를 kidly-app과 같은 주소 · 같은 응답 형식으로 내는지 본다.
 */
@FunctionalTest
class RecommendServiceApplicationApiTest {

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
}
