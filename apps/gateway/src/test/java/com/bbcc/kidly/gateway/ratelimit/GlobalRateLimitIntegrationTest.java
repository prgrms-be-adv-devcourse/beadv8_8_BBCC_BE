package com.bbcc.kidly.gateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.gateway.support.GatewayTestSupport;
import com.bbcc.kidly.gateway.support.IntegrationTest;
import com.bbcc.kidly.gateway.support.RecordingBackend;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec;

/**
 * 클라이언트(IP)별 한도(application-test.yml: 1시간에 1000번)를 넘으면 429로 막는지 본다(#1417, #1628).
 * 컨텍스트를 다른 테스트와 같이 쓰므로 테스트마다 처음 쓰는 IP로 보낸다.
 * 한도는 3.6초에 하나씩 다시 차므로, 막힐 때까지 보내고 통과한 수를 범위로 본다.
 */
@IntegrationTest
class GlobalRateLimitIntegrationTest extends GatewayTestSupport {

    private static final int CAPACITY = 1000;
    private static final String PATH = "/api/v1/nope";

    @Test
    @DisplayName("한도를 넘은 IP는 본문 없는 429로 막고 다른 IP는 통과시킨다")
    void limitsEachIp() {
        var ip = "203.0.113.201";
        assertThat(exhaust(() -> from(ip))).isBetween(CAPACITY, CAPACITY + 5);

        from(ip).expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
            .expectBody().isEmpty();
        from("203.0.113.202").expectStatus().isNotFound();
    }

    @Test
    @DisplayName("라우트마다 따로 센다. 한 라우트의 한도를 다 써도 다른 라우트는 통과한다")
    void countsEachRouteSeparately() {
        var ip = "203.0.113.203";
        exhaust(() -> from(ip));

        client.get().uri("/api/v1/products/1").header(RateLimitKeys.REAL_IP, ip).exchange()
            .expectStatus().isOk();
    }

    @Test
    @DisplayName("한도를 넘어도 토큰이 잘못된 요청은 429가 아니라 401이다")
    void unauthorizedComesBeforeLimit() {
        var ip = "203.0.113.204";
        exhaust(() -> from(ip));

        expectUnauthorized(client.get().uri(PATH).header(RateLimitKeys.REAL_IP, ip)
            .header(HttpHeaders.AUTHORIZATION, "Bearer invalid").exchange(), PATH);
    }

    @Test
    @DisplayName("밖에서 보낸 X-Member-Id로는 한도를 피할 수 없다. 토큰으로 확인한 주체만 키가 된다")
    void forgedPrincipalHeaderDoesNotChangeKey() {
        var ip = "203.0.113.205";
        exhaust(() -> from(ip));

        client.get().uri(PATH).header(RateLimitKeys.REAL_IP, ip).header("X-Member-Id", "7").exchange()
            .expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    @DisplayName("429에도 CORS 헤더가 붙는다")
    void corsOnTooManyRequests() {
        var ip = "203.0.113.206";
        exhaust(() -> from(ip));

        client.get().uri(PATH).header(RateLimitKeys.REAL_IP, ip)
            .header(HttpHeaders.ORIGIN, RecordingBackend.ALLOWED_ORIGIN).exchange()
            .expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
            .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, RecordingBackend.ALLOWED_ORIGIN);
    }

    // 429가 날 때까지 보내고 통과한 수를 돌려준다
    private static int exhaust(Supplier<ResponseSpec> request) {
        for (int passed = 0; passed <= CAPACITY + 5; passed++) {
            var status = request.get().returnResult(Void.class).getStatus();
            if (status.value() == HttpStatus.TOO_MANY_REQUESTS.value()) {
                return passed;
            }
        }
        return Integer.MAX_VALUE;
    }

    private ResponseSpec from(String ip) {
        return client.get().uri(PATH).header(RateLimitKeys.REAL_IP, ip).exchange();
    }
}
