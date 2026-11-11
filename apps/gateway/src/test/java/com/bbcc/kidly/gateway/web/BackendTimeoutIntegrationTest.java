package com.bbcc.kidly.gateway.web;

import com.bbcc.kidly.gateway.support.GatewayTestSupport;
import com.bbcc.kidly.gateway.support.IntegrationTest;
import com.bbcc.kidly.gateway.support.RecordingBackend;
import com.bbcc.kidly.gateway.support.RecordingBackendConfiguration;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

/**
 * 백엔드 응답이 늦으면 504 ProblemDetail을 내는지 본다(#668). application-test.yml이 response-timeout을 1초로 줄여
 * 테스트가 운영값(55초)만큼 기다리지 않는다. 추천 경로는 라우트에 따로 둔 제한(기본 3초, AI 경로는 기능마다 더 길게)을 쓰는지 본다.
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

        var response = client.get().uri("/api/v1/recommendations/products?childId=11").exchange();

        expectProblem(response, HttpStatus.GATEWAY_TIMEOUT, "응답이 지연되고 있습니다", "잠시 후 다시 시도해 주세요.",
            "/api/v1/recommendations/products");
    }

    // AI 답을 기다리는 추천 API는 추천 서비스가 기능마다 다르게 기다리므로(함께 사면 4초, 사이즈 3.5초, 맛보기 3초),
    // 그보다 0.5초 길게 기다려 추천 서비스가 규칙 결과로라도 답할 시간을 준다
    @ParameterizedTest
    @CsvSource({
        "/api/v1/recommendations/companions?productId=31&childId=11, 4000",
        "/api/v1/recommendations/size?productId=31&childId=11, 3500",
        "/api/v1/recommendations/products/preview?childId=11, 3200"
    })
    @DisplayName("AI 답을 기다리는 추천 경로는 기능마다 3초보다 길게 기다려, 그만큼 걸린 응답을 그대로 넘긴다")
    void aiRecommendRoutesWaitLongerByFeature(String uri, long delayMillis) {
        recommendBackend.delayResponse(Duration.ofMillis(delayMillis));

        client.get().uri(uri).exchange()
            .expectStatus().isOk();
    }

    // 스트림(SSE)은 규칙 결과를 먼저 보내고 AI 결과를 최대 10초 뒤에 보낸다. Gateway는 응답 조각 사이 간격으로 제한 시간을 재므로
    // 스트림 요청은 AI 대기보다 길게(15초) 기다려야 연결이 끊기지 않는다. 지연은 일반 요청의 제한 시간보다 길고 테스트 클라이언트의 5초보다 짧게 둔다
    @ParameterizedTest
    @CsvSource({
        "/api/v1/recommendations/companions?productId=31&childId=11, 4700",
        "/api/v1/recommendations/products/preview?childId=11, 4000"
    })
    @DisplayName("AI 추천 스트림(text/event-stream) 요청은 일반 요청의 제한 시간보다 늦은 응답도 그대로 넘긴다")
    void aiStreamRoutesWaitLonger(String uri, long delayMillis) {
        recommendBackend.delayResponse(Duration.ofMillis(delayMillis));

        client.get().uri(uri).accept(MediaType.TEXT_EVENT_STREAM).exchange()
            .expectStatus().isOk();
    }
}
