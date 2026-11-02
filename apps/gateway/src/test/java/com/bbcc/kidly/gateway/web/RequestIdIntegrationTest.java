package com.bbcc.kidly.gateway.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bbcc.kidly.gateway.support.GatewayTestSupport;
import com.bbcc.kidly.gateway.support.IntegrationTest;
import com.bbcc.kidly.gateway.support.RecordingBackend;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec;

/**
 * Gateway가 요청마다 X-Request-Id를 새로 만들어 붙이는지 본다(#683). 클라이언트가 보낸 값은 버리고,
 * 백엔드가 같은 이름으로 응답 헤더를 붙여도 값이 하나만 남는다.
 */
@IntegrationTest
class RequestIdIntegrationTest extends GatewayTestSupport {

    private static final String HEADER = "X-Request-Id";
    private static final Pattern UUID_PATTERN =
        Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

    private final Logger firewallLogger = (Logger) LoggerFactory.getLogger(RequestFirewallFilter.class);
    private final ListAppender<ILoggingEvent> firewallAppender = new ListAppender<>();

    @BeforeEach
    void attachFirewallAppender() {
        firewallAppender.start();
        firewallLogger.addAppender(firewallAppender);
    }

    @AfterEach
    void detachFirewallAppender() {
        firewallLogger.detachAppender(firewallAppender);
        firewallAppender.stop();
    }

    @Test
    @DisplayName("클라이언트가 보낸 X-Request-Id는 버리고 새로 만들어 백엔드로 보낸다")
    void overwritesClientRequestId() {
        var clientId = UUID.randomUUID().toString();

        client.get().uri("/api/v1/products/1").header(HEADER, clientId).exchange()
            .expectStatus().isOk();

        assertThat(backend.lastRequest()).hasValueSatisfying(
            request -> assertThat(request.header(HEADER)).singleElement()
                .satisfies(value -> {
                    assertThat(value).matches(UUID_PATTERN);
                    assertThat(value).isNotEqualTo(clientId);
                }));
    }

    @Test
    @DisplayName("백엔드가 응답에 같은 헤더를 붙여도 클라이언트 응답에는 값이 하나이고 백엔드가 받은 값과 같다")
    void keepsSingleResponseHeaderValue() {
        backend.addResponseHeader(HEADER, "backend-own-id");

        var response = client.get().uri("/api/v1/products/1").exchange().expectStatus().isOk();

        var sentId = backend.lastRequest().orElseThrow().header(HEADER).getFirst();
        assertThat(captureRequestId(response)).isEqualTo(sentId);
    }

    @Test
    @DisplayName("방화벽 400 응답에 UUID 값 하나가 붙고, 방화벽 로그의 traceId는 그 값과 같다")
    void addsHeaderToFirewallBadRequestAndMatchesFirewallLog() {
        var requestId = captureRequestId(client.get().uri(raw("/api/v1/../admin/members")).exchange()
            .expectStatus().isBadRequest());

        assertThat(requestId).matches(UUID_PATTERN);
        assertThat(firewallAppender.list.getFirst().getKeyValuePairs()).extracting(p -> p.key, p -> p.value)
            .contains(tuple("traceId", requestId));
    }

    @Test
    @DisplayName("토큰 없는 401 응답에 UUID 값 하나가 붙는다")
    void addsHeaderToUnauthorized() {
        expectSingleUuidHeader(client.get().uri("/api/v1/admin/members").exchange()
            .expectStatus().isUnauthorized());
    }

    @Test
    @DisplayName("CORS preflight 응답에 UUID 값 하나가 붙는다")
    void addsHeaderToPreflight() {
        expectSingleUuidHeader(client.options().uri("/api/v1/orders/1")
            .header(HttpHeaders.ORIGIN, RecordingBackend.ALLOWED_ORIGIN)
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.PATCH.name())
            .exchange()
            .expectStatus().isOk());
    }

    @Test
    @DisplayName("두 요청의 값은 서로 다르다")
    void differsBetweenRequests() {
        var first = captureRequestId(client.get().uri("/api/v1/products/1").exchange().expectStatus().isOk());
        var second = captureRequestId(client.get().uri("/api/v1/products/1").exchange().expectStatus().isOk());

        assertThat(first).isNotEqualTo(second);
    }

    private static void expectSingleUuidHeader(ResponseSpec response) {
        assertThat(captureRequestId(response)).matches(UUID_PATTERN);
    }

    private static String captureRequestId(ResponseSpec response) {
        var values = response.returnResult(Void.class).getResponseHeaders().get(HEADER);
        assertThat(values).hasSize(1);
        return values.getFirst();
    }
}
