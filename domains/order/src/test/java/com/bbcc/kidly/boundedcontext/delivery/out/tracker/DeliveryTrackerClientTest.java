package com.bbcc.kidly.boundedcontext.delivery.out.tracker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.bbcc.kidly.boundedcontext.delivery.out.config.DeliveryTrackerProperties;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Delivery Tracker 응답 해석. 실제 API를 부르지 않고 가짜 서버(MockRestServiceServer)로 10/15 실측 응답 모양을 재현한다.
 */
class DeliveryTrackerClientTest {

    static final String URL = "https://apis.tracker.delivery/graphql";
    static final DeliveryTrackerProperties ENABLED = new DeliveryTrackerProperties("https://apis.tracker.delivery",
        "id", "secret", Duration.ofSeconds(3), Duration.ofSeconds(10), Duration.ZERO);

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final DeliveryTrackerClient client = new DeliveryTrackerClient(ENABLED, builder);

    @Test
    @DisplayName("lastEvent가 있으면 인증 헤더 · 변수로 요청하고 상태 코드와 이벤트 시각을 돌려준다")
    void returnsStatusAndTime() {
        server.expect(requestTo(URL))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", "TRACKQL-API-KEY id:secret"))
            .andExpect(jsonPath("$.variables.carrierId").value("kr.cjlogistics"))
            .andExpect(jsonPath("$.variables.trackingNumber").value("1234567890"))
            .andRespond(withSuccess("""
                {"data":{"track":{"lastEvent":{"time":"2026-10-15T09:00:00Z","status":{"code":"DELIVERED"}}}}}
                """, MediaType.APPLICATION_JSON));

        assertThat(client.track("kr.cjlogistics", "1234567890"))
            .isEqualTo(new TrackResult("DELIVERED", OffsetDateTime.parse("2026-10-15T09:00:00Z")));
        server.verify();
    }

    @Test
    @DisplayName("택배사가 준 오프셋(+09:00) · 밀리초를 보존한다")
    void keepsOffset() {
        server.expect(requestTo(URL)).andRespond(withSuccess("""
            {"data":{"track":{"lastEvent":{"time":"2026-07-02T15:11:06.000+09:00","status":{"code":"DELIVERED"}}}}}
            """, MediaType.APPLICATION_JSON));

        TrackResult result = client.track("kr.cjlogistics", "1234567890");

        assertThat(result.eventTime().toInstant()).isEqualTo(Instant.parse("2026-07-02T06:11:06Z"));
    }

    @Test
    @DisplayName("lastEvent가 null이면 UNKNOWN과 시각 null을 돌려준다")
    void unknownWhenNoEvent() {
        server.expect(requestTo(URL))
            .andRespond(withSuccess("{\"data\":{\"track\":{\"lastEvent\":null}}}", MediaType.APPLICATION_JSON));

        assertThat(client.track("kr.cjlogistics", "1234567890")).isEqualTo(new TrackResult("UNKNOWN", null));
    }

    @Test
    @DisplayName("시각은 DELIVERED일 때만 필수다: 배송 중 이벤트에 시각이 없거나 못 읽으면 오류가 아니라 시각 null로 돌려준다")
    void timeOptionalUnlessDelivered() {
        server.expect(requestTo(URL)).andRespond(withSuccess("""
            {"data":{"track":{"lastEvent":{"status":{"code":"IN_TRANSIT"}}}}}
            """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(URL)).andRespond(withSuccess("""
            {"data":{"track":{"lastEvent":{"time":"2026-07-02 15:11","status":{"code":"IN_TRANSIT"}}}}}
            """, MediaType.APPLICATION_JSON));

        assertThat(client.track("kr.cjlogistics", "1234567890")).isEqualTo(new TrackResult("IN_TRANSIT", null));
        assertThat(client.track("kr.cjlogistics", "1234567890")).isEqualTo(new TrackResult("IN_TRANSIT", null));
    }

    @Test
    @DisplayName("200 응답의 GraphQL errors는 extensions.code로 예외가 되고, BAD_REQUEST만 번호 형식 오류다")
    void graphqlErrorsOn200() {
        server.expect(requestTo(URL)).andRespond(withSuccess("""
            {"errors":[{"message":"운송장 번호는 10자리 혹은 12자리입니다.",
              "extensions":{"code":"BAD_REQUEST"}}],"data":{"track":null}}
            """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(URL)).andRespond(withSuccess("""
            {"errors":[{"message":"","extensions":{"code":"NOT_FOUND"}}],"data":{"track":null}}
            """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(URL)).andRespond(withSuccess("""
            {"errors":[{"message":"rate limit exceeded.","extensions":{"code":"FORBIDDEN"}}]}
            """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.track("kr.cjlogistics", "abc"))
            .isInstanceOfSatisfying(DeliveryTrackerException.class, e -> {
                assertThat(e.code()).isEqualTo("BAD_REQUEST");
                assertThat(e.isInvalidTrackingNumber()).isTrue();
            });
        assertThatThrownBy(() -> client.track("kr.hanjin", "000000000000"))
            .isInstanceOfSatisfying(DeliveryTrackerException.class, e -> {
                assertThat(e.code()).isEqualTo("NOT_FOUND");
                assertThat(e.isInvalidTrackingNumber()).isFalse();
            });
        assertThatThrownBy(() -> client.track("kr.cjlogistics", "1234567890"))
            .isInstanceOfSatisfying(DeliveryTrackerException.class, e -> assertThat(e.code()).isEqualTo("FORBIDDEN"));
    }

    @Test
    @DisplayName("4xx 본문에 errors가 있으면 HTTP 상태보다 extensions.code를 쓴다")
    void prefersGraphqlCode() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED)
            .contentType(MediaType.APPLICATION_JSON)
            .body("{\"errors\":[{\"message\":\"Invalid or expired token.\","
                + "\"extensions\":{\"code\":\"UNAUTHENTICATED\"}}]}"));

        assertThatThrownBy(() -> client.track("kr.cjlogistics", "1234567890"))
            .isInstanceOfSatisfying(DeliveryTrackerException.class,
                e -> assertThat(e.code()).isEqualTo("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("errors 없는 오류 응답(HTML)이면 HTTP_상태 코드로 예외를 던진다")
    void httpStatusWithoutErrors() {
        server.expect(requestTo(URL))
            .andRespond(withStatus(HttpStatus.BAD_GATEWAY).contentType(MediaType.TEXT_HTML).body("<html>502</html>"));

        assertThatThrownBy(() -> client.track("kr.cjlogistics", "1234567890"))
            .isInstanceOfSatisfying(DeliveryTrackerException.class, e -> assertThat(e.code()).isEqualTo("HTTP_502"));
    }

    @Test
    @DisplayName("track이 없거나 시각을 읽을 수 없으면 INVALID_RESPONSE다")
    void invalidResponse() {
        server.expect(requestTo(URL)).andRespond(withSuccess("{\"data\":{}}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(URL)).andRespond(withSuccess("""
            {"data":{"track":{"lastEvent":{"time":"2026-07-02 15:11","status":{"code":"DELIVERED"}}}}}
            """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.track("kr.cjlogistics", "1234567890"))
            .isInstanceOfSatisfying(DeliveryTrackerException.class,
                e -> assertThat(e.code()).isEqualTo("INVALID_RESPONSE"));
        assertThatThrownBy(() -> client.track("kr.cjlogistics", "1234567890"))
            .isInstanceOfSatisfying(DeliveryTrackerException.class,
                e -> assertThat(e.code()).isEqualTo("INVALID_RESPONSE"));
    }

    @Test
    @DisplayName("인증값이 비었거나 헤더에 못 넣는 문자(한글 · 공백)면 비활성이고, 요청 없이 DISABLED다")
    void disabled() {
        RestClient.Builder emptyBuilder = RestClient.builder();
        MockRestServiceServer noCall = MockRestServiceServer.bindTo(emptyBuilder).build();
        DeliveryTrackerClient disabled = new DeliveryTrackerClient(new DeliveryTrackerProperties(
            "https://apis.tracker.delivery", "", null, Duration.ofSeconds(3), Duration.ofSeconds(10), Duration.ZERO),
            emptyBuilder);

        assertThat(disabled.isEnabled()).isFalse();
        assertThatThrownBy(() -> disabled.track("kr.cjlogistics", "1234567890"))
            .isInstanceOfSatisfying(DeliveryTrackerException.class, e -> assertThat(e.code()).isEqualTo("DISABLED"));
        noCall.verify();
        assertThat(new DeliveryTrackerProperties("u", "발급받은_ID", "secret", null, null, null).enabled()).isFalse();
        assertThat(new DeliveryTrackerProperties("u", "id", "sec ret", null, null, null).enabled()).isFalse();
        assertThat(ENABLED.toString()).doesNotContain("secret").doesNotContain("id:");
    }
}
