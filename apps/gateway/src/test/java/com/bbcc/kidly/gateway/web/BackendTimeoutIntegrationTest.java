package com.bbcc.kidly.gateway.web;

import com.bbcc.kidly.gateway.support.GatewayTestSupport;
import com.bbcc.kidly.gateway.support.IntegrationTest;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * 백엔드 응답이 늦으면 504 ProblemDetail을 내는지 본다(#668). application-test.yml이 response-timeout을 1초로 줄여
 * 테스트가 운영값(55초)만큼 기다리지 않는다.
 */
@IntegrationTest
class BackendTimeoutIntegrationTest extends GatewayTestSupport {

    @Test
    @DisplayName("백엔드 응답이 늦으면 504 ProblemDetail이다")
    void respondsGatewayTimeout() {
        backend.delayResponse(Duration.ofSeconds(2));

        var response = client.get().uri("/api/v1/members/me").exchange();

        expectProblem(response, HttpStatus.GATEWAY_TIMEOUT, "응답이 지연되고 있습니다", "잠시 후 다시 시도해 주세요.",
            "/api/v1/members/me");
        response.expectHeader().exists("X-Request-Id");
    }
}
