package com.bbcc.kidly.gateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.gateway.support.GatewayTestSupport;
import com.bbcc.kidly.gateway.support.IntegrationTest;
import com.bbcc.kidly.gateway.support.RecordingBackend;
import com.bbcc.kidly.gateway.support.RecordingBackendConfiguration;
import com.bbcc.kidly.gateway.support.TestTokens;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec;

/**
 * 검색어가 있는 상품 목록 요청을 회원 · IP마다 분당 30번까지 넘기고, 넘으면 429로 막는지 본다(#816).
 * 컨텍스트를 다른 테스트와 같이 쓰므로(한도도 같이 쓴다) 테스트마다 처음 쓰는 IP · 회원 ID로 보낸다.
 * 한도는 2초에 하나씩 다시 차므로, 보내는 사이 시간이 걸려도 깨지지 않게 막힐 때까지 보내고 통과한 수를 범위로 본다.
 */
@IntegrationTest
class SearchRateLimitIntegrationTest extends GatewayTestSupport {

    private static final int CAPACITY = 30;
    private static final String SEARCH = "/api/v1/products?keyword=물놀이";
    private static final AtomicInteger NEXT = new AtomicInteger(1);

    @Autowired
    @Qualifier(RecordingBackendConfiguration.SEARCH)
    private RecordingBackend searchBackend;

    @Test
    @DisplayName("같은 IP의 검색은 30번까지 넘기고, 31번째는 429 ProblemDetail과 Retry-After로 막고 넘기지 않는다")
    void limitsByIp() {
        var ip = uniqueIp();
        assertThat(exhaust(() -> searchFrom(ip))).isBetween(CAPACITY, CAPACITY + 5);
        searchBackend.reset();

        var response = searchFrom(ip);

        expectProblem(response, HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 많습니다", "잠시 후 다시 시도해 주세요.",
            "/api/v1/products");
        // 하나가 다시 차는 데 60초 / 30 = 2초
        response.expectHeader().valueMatches(HttpHeaders.RETRY_AFTER, "[12]");
        assertThat(searchBackend.lastRequest()).isEmpty();
        // 다른 IP는 따로 센다
        searchFrom(uniqueIp()).expectStatus().isOk();
    }

    @Test
    @DisplayName("로그인 회원은 IP가 아니라 회원으로 센다. 같은 IP의 다른 회원 · 비로그인은 막히지 않는다")
    void limitsByMember() {
        var ip = uniqueIp();
        var member = token(NEXT.incrementAndGet() + 100_000L);
        assertThat(exhaust(() -> searchAs(member, ip))).isBetween(CAPACITY, CAPACITY + 5);

        searchAs(member, uniqueIp()).expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        searchAs(token(NEXT.incrementAndGet() + 100_000L), ip).expectStatus().isOk();
        searchFrom(ip).expectStatus().isOk();
    }

    @Test
    @DisplayName("검색어 없는 목록 · 빈 검색어 · 상품 상세는 세지 않는다")
    void ignoresRequestsWithoutKeyword() {
        var ip = uniqueIp();
        for (String path : List.of("/api/v1/products", "/api/v1/products?keyword=%20%20", "/api/v1/products/1")) {
            for (int i = 0; i <= CAPACITY; i++) {
                client.get().uri(raw(path)).header(SearchRateLimitFilter.REAL_IP, ip).exchange().expectStatus().isOk();
            }
        }
        searchFrom(ip).expectStatus().isOk();
    }

    @Test
    @DisplayName("keyword가 여러 번 오면 첫 값이 비어 있어도 하나라도 있으면 센다")
    void countsRepeatedKeyword() {
        var ip = uniqueIp();
        assertThat(exhaust(() -> client.get().uri(raw("/api/v1/products?keyword=&keyword=%EC%88%98%EC%98%81%EB%B3%B5"))
            .header(SearchRateLimitFilter.REAL_IP, ip).exchange())).isBetween(CAPACITY, CAPACITY + 5);
    }

    @Test
    @DisplayName("429에도 CORS 헤더가 붙고 Retry-After를 프론트가 읽을 수 있다")
    void corsOnTooManyRequests() {
        var ip = uniqueIp();
        exhaust(() -> searchFrom(ip));

        client.get().uri(SEARCH).header(SearchRateLimitFilter.REAL_IP, ip)
            .header(HttpHeaders.ORIGIN, RecordingBackend.ALLOWED_ORIGIN).exchange()
            .expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
            .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, RecordingBackend.ALLOWED_ORIGIN)
            .expectHeader().value(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS,
                exposed -> assertThat(exposed).contains(HttpHeaders.RETRY_AFTER));
    }

    // 429가 날 때까지 보내고 통과한 수를 돌려준다
    private static int exhaust(Supplier<ResponseSpec> request) {
        for (int passed = 0; passed <= CAPACITY + 5; passed++) {
            var status = request.get().returnResult(Void.class).getStatus();
            if (status.value() == HttpStatus.TOO_MANY_REQUESTS.value()) {
                return passed;
            }
            assertThat(status.is2xxSuccessful()).isTrue();
        }
        return Integer.MAX_VALUE;
    }

    private ResponseSpec searchFrom(String ip) {
        return client.get().uri(SEARCH).header(SearchRateLimitFilter.REAL_IP, ip).exchange();
    }

    private ResponseSpec searchAs(String token, String ip) {
        return client.get().uri(SEARCH).header(SearchRateLimitFilter.REAL_IP, ip)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).exchange();
    }

    private static String token(long memberId) {
        var now = Instant.now();
        return TestTokens.issue(memberId, List.of("USER"), now, now.plusSeconds(1800));
    }

    // 문서용 주소 대역(TEST-NET-3)
    private static String uniqueIp() {
        int n = NEXT.incrementAndGet();
        return "203.0." + (n / 250) + "." + (n % 250 + 1);
    }
}
