package com.bbcc.kidly.gateway.web;

import com.bbcc.kidly.gateway.support.GatewayTestSupport;
import com.bbcc.kidly.gateway.support.IntegrationTest;
import com.bbcc.kidly.gateway.support.RecordingBackend;
import com.bbcc.kidly.gateway.support.RecordingBackendConfiguration;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;

/**
 * 백엔드 응답이 늦으면 504 ProblemDetail을 내는지 본다(#668). application-test.yml이 response-timeout을 1초로 줄여
 * 테스트가 운영값(55초)만큼 기다리지 않는다. 추천 경로는 라우트에 따로 둔 3초 제한을 쓰는지 본다(#704).
 */
@IntegrationTest
class BackendTimeoutIntegrationTest extends GatewayTestSupport {

    @Autowired
    @Qualifier(RecordingBackendConfiguration.RECOMMEND)
    private RecordingBackend recommendBackend;

    // 추천 백엔드는 GatewayTestSupport가 비우지 않으므로, 늦춘 응답이 다른 테스트에 남지 않게 지운다
    @AfterEach
    void resetRecommendBackend() {
        recommendBackend.reset();
    }

    @Test
    @DisplayName("백엔드 응답이 늦으면 504 ProblemDetail이다")
    void respondsGatewayTimeout() {
        orderBackend.delayResponse(Duration.ofSeconds(2));

        var response = client.get().uri("/api/v1/orders").exchange();

        expectProblem(response, HttpStatus.GATEWAY_TIMEOUT, "응답이 지연되고 있습니다", "잠시 후 다시 시도해 주세요.",
            "/api/v1/orders");
        response.expectHeader().exists("X-Request-Id");
    }

    @Test
    @DisplayName("추천 경로는 전체 응답 제한(테스트 1초)이 아니라 자기 제한(3초)을 써서, 2초 걸린 응답을 그대로 넘긴다")
    void recommendRouteWaitsLongerThanDefault() {
        recommendBackend.delayResponse(Duration.ofSeconds(2));

        client.get().uri("/api/v1/recommendations/products?childId=11").exchange()
            .expectStatus().isOk();
    }

    @Test
    @DisplayName("추천 경로는 3초가 지나면 504 ProblemDetail로 끊는다")
    void recommendRouteTimesOutAfterThreeSeconds() {
        recommendBackend.delayResponse(Duration.ofSeconds(4));

        var response = client.get().uri("/api/v1/recommendations/size?productId=31&childId=11").exchange();

        expectProblem(response, HttpStatus.GATEWAY_TIMEOUT, "응답이 지연되고 있습니다", "잠시 후 다시 시도해 주세요.",
            "/api/v1/recommendations/size");
    }
}
