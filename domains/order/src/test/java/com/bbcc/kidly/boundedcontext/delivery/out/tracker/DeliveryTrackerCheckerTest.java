package com.bbcc.kidly.boundedcontext.delivery.out.tracker;

import static com.bbcc.kidly.boundedcontext.delivery.out.tracker.DeliveryTrackerClientTest.ENABLED;
import static com.bbcc.kidly.boundedcontext.delivery.out.tracker.DeliveryTrackerClientTest.URL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.bbcc.kidly.boundedcontext.delivery.app.port.DeliveryStatusChecker.TrackingResult;
import com.bbcc.kidly.boundedcontext.delivery.domain.Delivery;
import com.bbcc.kidly.boundedcontext.delivery.out.config.DeliveryTrackerProperties;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * 택배사 enum → Delivery Tracker ID, 응답 → TrackingResult 변환. 서버 시간대는 KST로 고정해 확인한다.
 */
class DeliveryTrackerCheckerTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final DeliveryTrackerChecker checker =
        new DeliveryTrackerChecker(new DeliveryTrackerClient(ENABLED, builder), Duration.ZERO, KST);

    @ParameterizedTest(name = "{0}")
    @EnumSource(Delivery.Carrier.class)
    @DisplayName("모든 택배사 enum에 Delivery Tracker ID가 있다(enum을 늘리면 여기서 걸린다)")
    void everyCarrierHasId(Delivery.Carrier carrier) {
        assertThat(DeliveryTrackerChecker.CARRIER_IDS).containsKey(carrier);
    }

    @Test
    @DisplayName("DUMMY는 dev.track.dummy, CJ는 kr.cjlogistics로 조회한다")
    void mapsCarrierId() {
        server.expect(requestTo(URL)).andExpect(jsonPath("$.variables.carrierId").value("dev.track.dummy"))
            .andRespond(withSuccess(inTransit(), MediaType.APPLICATION_JSON));
        server.expect(requestTo(URL)).andExpect(jsonPath("$.variables.carrierId").value("kr.cjlogistics"))
            .andRespond(withSuccess(inTransit(), MediaType.APPLICATION_JSON));

        checker.check(Delivery.Carrier.DUMMY, "2026-11-01T09:00:00Z");
        checker.check(Delivery.Carrier.CJ, "1234567890");

        server.verify();
    }

    @Test
    @DisplayName("DELIVERED면 택배사 시각(Z · +09:00)을 서버 시간대(KST) LocalDateTime으로 바꿔 돌려준다")
    void deliveredInServerZone() {
        server.expect(requestTo(URL)).andRespond(withSuccess("""
            {"data":{"track":{"lastEvent":{"time":"2026-11-01T06:00:00.000Z","status":{"code":"DELIVERED"}}}}}
            """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(URL)).andRespond(withSuccess("""
            {"data":{"track":{"lastEvent":{"time":"2026-11-01T15:00:00.000+09:00","status":{"code":"DELIVERED"}}}}}
            """, MediaType.APPLICATION_JSON));

        assertThat(checker.check(Delivery.Carrier.DUMMY, "2026-11-01T06:00:00Z"))
            .isEqualTo(TrackingResult.delivered(LocalDateTime.of(2026, 11, 1, 15, 0)));
        assertThat(checker.check(Delivery.Carrier.CJ, "1234567890"))
            .isEqualTo(TrackingResult.delivered(LocalDateTime.of(2026, 11, 1, 15, 0)));
    }

    @Test
    @DisplayName("DELIVERED가 아니거나 이벤트가 없으면 IN_PROGRESS다")
    void inProgress() {
        server.expect(requestTo(URL)).andRespond(withSuccess(inTransit(), MediaType.APPLICATION_JSON));
        server.expect(requestTo(URL))
            .andRespond(withSuccess("{\"data\":{\"track\":{\"lastEvent\":null}}}", MediaType.APPLICATION_JSON));

        assertThat(checker.check(Delivery.Carrier.DUMMY, "2026-11-01T09:00:00Z"))
            .isEqualTo(TrackingResult.inProgress());
        assertThat(checker.check(Delivery.Carrier.CJ, "1234567890")).isEqualTo(TrackingResult.inProgress());
    }

    @Test
    @DisplayName("BAD_REQUEST는 번호 형식 오류, NOT_FOUND · FORBIDDEN은 다음에 다시 조회(UNAVAILABLE)다")
    void errorsToResult() {
        server.expect(requestTo(URL)).andRespond(withSuccess(error("BAD_REQUEST"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(URL)).andRespond(withSuccess(error("NOT_FOUND"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(URL)).andRespond(withSuccess(error("FORBIDDEN"), MediaType.APPLICATION_JSON));

        assertThat(checker.check(Delivery.Carrier.DUMMY, "test"))
            .isEqualTo(TrackingResult.invalidTrackingNumber("BAD_REQUEST"));
        assertThat(checker.check(Delivery.Carrier.HANJIN, "000000000000"))
            .isEqualTo(TrackingResult.unavailable("NOT_FOUND"));
        assertThat(checker.check(Delivery.Carrier.CJ, "1234567890"))
            .isEqualTo(TrackingResult.unavailable("FORBIDDEN"));
    }

    @Test
    @DisplayName("인증값이 없으면 비활성이다")
    void disabled() {
        DeliveryTrackerChecker disabled = new DeliveryTrackerChecker(new DeliveryTrackerClient(
            new DeliveryTrackerProperties("https://apis.tracker.delivery", "", "", Duration.ofSeconds(3),
                Duration.ofSeconds(10), Duration.ZERO), RestClient.builder()), Duration.ZERO, KST);

        assertThat(disabled.isEnabled()).isFalse();
    }

    private static String inTransit() {
        return "{\"data\":{\"track\":{\"lastEvent\":"
            + "{\"time\":\"2026-11-01T06:00:00.000Z\",\"status\":{\"code\":\"IN_TRANSIT\"}}}}}";
    }

    private static String error(String code) {
        return "{\"errors\":[{\"message\":\"\",\"extensions\":{\"code\":\"" + code + "\"}}],\"data\":{\"track\":null}}";
    }
}
