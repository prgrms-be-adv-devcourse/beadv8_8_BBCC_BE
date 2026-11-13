package com.bbcc.kidly.boundedcontext.delivery.out.tracker;

import static com.bbcc.kidly.boundedcontext.delivery.out.tracker.DeliveryTrackerClientTest.ENABLED;
import static com.bbcc.kidly.boundedcontext.delivery.out.tracker.DeliveryTrackerClientTest.URL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.bbcc.kidly.boundedcontext.delivery.app.port.DeliveryStatusChecker;
import com.bbcc.kidly.boundedcontext.delivery.app.port.DeliveryStatusChecker.TrackingEvent;
import com.bbcc.kidly.boundedcontext.delivery.app.port.DeliveryStatusChecker.TrackingHistory;
import com.bbcc.kidly.boundedcontext.delivery.app.port.DeliveryStatusChecker.TrackingResult;
import com.bbcc.kidly.boundedcontext.delivery.domain.Delivery;
import com.bbcc.kidly.boundedcontext.delivery.out.config.DeliveryTrackerProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * 구매자 택배 진행 이력 조회(#1320): 요청 칸(시각 · 상태 코드 · 장소 이름만, 최근 30개 상한), 응답 변환(최신 먼저 · 서버 시간대),
 * 오류 · 서킷 처리. 서버 시간대는 KST로 고정해 확인한다.
 */
class TrackingHistoryCheckerTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final TrackerCircuitBreaker circuitBreaker =
        TrackerCircuitBreaker.create(DeliveryTrackerCircuitBreakerTest.DEFAULTS, Clock.systemUTC());
    private final DeliveryTrackerChecker checker = new DeliveryTrackerChecker(
        new DeliveryTrackerClient(ENABLED, builder), circuitBreaker, Duration.ZERO, KST);

    @Test
    @DisplayName("요청: 그 택배사 ID · 운송장으로 events(last: 30)만, 칸은 시각 · 상태 코드 · 장소 이름뿐(설명 · 연락처 · 보내는/받는 사람 없음)")
    void requestsOnlyNeededFields() {
        server.expect(requestTo(URL))
            .andExpect(jsonPath("$.variables.carrierId").value("kr.hanjin"))
            .andExpect(jsonPath("$.variables.trackingNumber").value("123456789012"))
            .andExpect(jsonPath("$.variables.last").value(DeliveryTrackerClient.EVENTS_LIMIT))
            .andExpect(request -> {
                String body = ((MockClientHttpRequest) request).getBodyAsString();
                assertThat(body).contains("events(last: $last)", "time", "status { code }", "location { name }")
                    .doesNotContain("description", "contact", "sender", "recipient", "phoneNumber", "lastEvent");
            })
            .andRespond(withSuccess(events(), MediaType.APPLICATION_JSON));

        checker.history(Delivery.Carrier.HANJIN, "123456789012");

        server.verify();
        assertThat(DeliveryTrackerClient.EVENTS_LIMIT).isEqualTo(30);
    }

    @Test
    @DisplayName("응답 이벤트(오래된 것부터)를 최신 먼저로 뒤집고, 시각(Z · +09:00)은 서버 시간대로, 장소 · 시각이 없으면 null로 담는다")
    void mapsEventsNewestFirst() {
        server.expect(requestTo(URL)).andRespond(withSuccess(events(), MediaType.APPLICATION_JSON));

        TrackingHistory history = checker.history(Delivery.Carrier.CJ, "1234567890");

        assertThat(history).isEqualTo(TrackingHistory.ok(List.of(
            new TrackingEvent(null, "고객", "DELIVERED"),
            new TrackingEvent(LocalDateTime.of(2026, 11, 12, 9, 30), null, "OUT_FOR_DELIVERY"),
            new TrackingEvent(LocalDateTime.of(2026, 11, 11, 18, 0), "대전HUB", "IN_TRANSIT"),
            new TrackingEvent(LocalDateTime.of(2026, 11, 11, 15, 0), "서울", "AT_PICKUP"))));
    }

    @Test
    @DisplayName("이벤트가 없으면(events null · 빈 edges) 빈 이력이다")
    void noEventsIsEmpty() {
        server.expect(requestTo(URL))
            .andRespond(withSuccess("{\"data\":{\"track\":{\"events\":null}}}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(URL))
            .andRespond(withSuccess("{\"data\":{\"track\":{\"events\":{\"edges\":[]}}}}", MediaType.APPLICATION_JSON));

        assertThat(checker.history(Delivery.Carrier.CJ, "1")).isEqualTo(TrackingHistory.ok(List.of()));
        assertThat(checker.history(Delivery.Carrier.CJ, "2")).isEqualTo(TrackingHistory.ok(List.of()));
    }

    @Test
    @DisplayName("테스트 택배사 DUMMY는 부르지 않고 조회할 수 없는 택배사다")
    void dummyIsUnsupported() {
        assertThat(checker.history(Delivery.Carrier.DUMMY, "2026-11-12T09:00:00Z"))
            .isEqualTo(TrackingHistory.unsupportedCarrier());
        server.verify();
    }

    @Test
    @DisplayName("조회가 꺼져 있으면(인증값 없음) 부르지 않고 DISABLED 일시 오류다. DUMMY는 꺼져 있어도 조회할 수 없는 택배사다")
    void disabledIsUnavailableWithoutCall() {
        DeliveryTrackerProperties off = new DeliveryTrackerProperties("https://apis.tracker.delivery", "", "",
            ENABLED.connectTimeout(), ENABLED.readTimeout(), Duration.ZERO);
        DeliveryTrackerChecker disabled = new DeliveryTrackerChecker(new DeliveryTrackerClient(off, builder),
            circuitBreaker, Duration.ZERO, KST);

        assertThat(disabled.history(Delivery.Carrier.HANJIN, "1")).isEqualTo(TrackingHistory.unavailable("DISABLED"));
        assertThat(disabled.history(Delivery.Carrier.DUMMY, "1")).isEqualTo(TrackingHistory.unsupportedCarrier());
        assertThat(circuitBreaker.state()).isEqualTo(CircuitBreaker.State.CLOSED);
        server.verify();
    }

    @Test
    @DisplayName("택배사에 아직 등록 전(NOT_FOUND)은 빈 이력, 형식 오류(BAD_REQUEST)는 운송장 오류, 그 밖(FORBIDDEN · 응답 이상)은 일시 오류다")
    void errorsToHistory() {
        server.expect(requestTo(URL)).andRespond(withSuccess(error("NOT_FOUND"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(URL)).andRespond(withSuccess(error("BAD_REQUEST"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(URL)).andRespond(withSuccess(error("FORBIDDEN"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(URL)).andRespond(withSuccess("{\"data\":{}}", MediaType.APPLICATION_JSON));

        assertThat(checker.history(Delivery.Carrier.HANJIN, "1")).isEqualTo(TrackingHistory.notRegistered());
        assertThat(checker.history(Delivery.Carrier.HANJIN, "2"))
            .isEqualTo(TrackingHistory.invalidTrackingNumber("BAD_REQUEST"));
        assertThat(checker.history(Delivery.Carrier.HANJIN, "3")).isEqualTo(TrackingHistory.unavailable("FORBIDDEN"));
        assertThat(checker.history(Delivery.Carrier.HANJIN, "4"))
            .isEqualTo(TrackingHistory.unavailable("INVALID_RESPONSE"));
    }

    @Test
    @DisplayName("구매자 진행 이력은 상태 조회(10분 주기)와 서킷을 따로 센다: 이력 실패로 이력 서킷이 열려도 상태 조회는 그대로 부른다")
    void historyFailuresDoNotOpenStatusCircuit() {
        server.expect(times(10), requestTo(URL))
            .andRespond(withSuccess(error("FORBIDDEN"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(URL)).andRespond(withSuccess(
            "{\"data\":{\"track\":{\"lastEvent\":null}}}", MediaType.APPLICATION_JSON));
        for (int i = 0; i < 10; i++) {
            checker.history(Delivery.Carrier.CJ, "1234567890");
        }
        assertThat(checker.history(Delivery.Carrier.CJ, "1234567890"))
            .as("이력 서킷은 열렸다").isEqualTo(TrackingHistory.unavailable(TrackingResult.CIRCUIT_OPEN));
        assertThat(circuitBreaker.state()).as("상태 조회 서킷은 닫혀 있다").isEqualTo(CircuitBreaker.State.CLOSED);

        assertThat(checker.check(Delivery.Carrier.CJ, "1234567890")).isEqualTo(TrackingResult.inProgress());
        server.verify();
    }

    @Test
    @DisplayName("반대로 상태 조회 실패로 상태 서킷이 열려도 구매자 진행 이력은 그대로 부른다")
    void statusFailuresDoNotBlockHistory() {
        server.expect(times(10), requestTo(URL))
            .andRespond(withSuccess(error("FORBIDDEN"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(URL)).andRespond(withSuccess(events(), MediaType.APPLICATION_JSON));
        for (int i = 0; i < 10; i++) {
            checker.check(Delivery.Carrier.CJ, "1234567890");
        }
        assertThat(circuitBreaker.state()).isEqualTo(CircuitBreaker.State.OPEN);

        assertThat(checker.history(Delivery.Carrier.CJ, "1234567890").kind())
            .isEqualTo(DeliveryStatusChecker.HistoryKind.OK);
        server.verify();
    }

    @Test
    @DisplayName("이력 서킷이 열려도 열림 시간(1분)이 지나면 다시 불러 보고(반열림), 시험 호출 3번이 성공하면 닫힌다(재기동 없이 회복)")
    void historyCircuitRecoversAfterOpenWait() {
        MutableClock clock = new MutableClock();
        RestClient.Builder clockBuilder = RestClient.builder();
        MockRestServiceServer clockServer = MockRestServiceServer.bindTo(clockBuilder).build();
        TrackerCircuitBreaker history = TrackerCircuitBreaker.create(DeliveryTrackerCircuitBreakerTest.DEFAULTS, clock);
        DeliveryTrackerChecker clocked = new DeliveryTrackerChecker(new DeliveryTrackerClient(ENABLED, clockBuilder),
            TrackerCircuitBreaker.create(DeliveryTrackerCircuitBreakerTest.DEFAULTS, clock), history, Duration.ZERO,
            DeliveryTrackerChecker.HISTORY_MAX_WAIT, KST);
        clockServer.expect(times(10), requestTo(URL))
            .andRespond(withSuccess(error("FORBIDDEN"), MediaType.APPLICATION_JSON));
        clockServer.expect(times(3), requestTo(URL)).andRespond(withSuccess(events(), MediaType.APPLICATION_JSON));
        clockServer.expect(requestTo(URL)).andRespond(withSuccess(events(), MediaType.APPLICATION_JSON));

        for (int i = 0; i < 10; i++) {
            clocked.history(Delivery.Carrier.CJ, "1234567890");
        }
        assertThat(history.state()).isEqualTo(CircuitBreaker.State.OPEN);
        clock.advance(Duration.ofSeconds(59));
        assertThat(clocked.history(Delivery.Carrier.CJ, "1234567890"))
            .as("열림 유지 중에는 부르지 않는다").isEqualTo(TrackingHistory.unavailable(TrackingResult.CIRCUIT_OPEN));

        clock.advance(Duration.ofSeconds(2));
        for (int i = 0; i < 3; i++) {
            assertThat(clocked.history(Delivery.Carrier.CJ, "1234567890").kind())
                .isEqualTo(DeliveryStatusChecker.HistoryKind.OK);
        }
        assertThat(history.state()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(clocked.history(Delivery.Carrier.CJ, "1234567890").kind())
            .isEqualTo(DeliveryStatusChecker.HistoryKind.OK);
        clockServer.verify();
    }

    @Test
    @DisplayName("호출 간격 자리를 못 얻으면 받은 서킷 허락을 돌려준다: 반열림 시험 자리가 새지 않아 다음 시험 호출이 그대로 된다")
    void busySlotReleasesPermission() {
        MutableClock clock = new MutableClock();
        RestClient.Builder clockBuilder = RestClient.builder();
        MockRestServiceServer clockServer = MockRestServiceServer.bindTo(clockBuilder).build();
        TrackerCircuitBreaker history = TrackerCircuitBreaker.create(DeliveryTrackerCircuitBreakerTest.DEFAULTS, clock);
        DeliveryTrackerChecker spaced = new DeliveryTrackerChecker(new DeliveryTrackerClient(ENABLED, clockBuilder),
            TrackerCircuitBreaker.create(DeliveryTrackerCircuitBreakerTest.DEFAULTS, clock), history,
            Duration.ofSeconds(5), DeliveryTrackerChecker.HISTORY_MAX_WAIT, KST);
        clockServer.expect(times(10), requestTo(URL))
            .andRespond(withSuccess(error("FORBIDDEN"), MediaType.APPLICATION_JSON));
        for (int i = 0; i < 10; i++) {
            // 간격 5초라 첫 호출 뒤에는 자리를 못 얻는다: 실제로 부른 건 첫 번째뿐이므로 실패를 10번 쌓으려면 간격을 비운다
            ReflectionTestUtils.setField(spaced, "lastCallNanos", 0L);
            spaced.history(Delivery.Carrier.CJ, "1234567890");
        }
        assertThat(history.state()).isEqualTo(CircuitBreaker.State.OPEN);
        clock.advance(Duration.ofSeconds(61));

        assertThat(spaced.history(Delivery.Carrier.CJ, "1234567890"))
            .as("반열림 허락은 받았지만 자리가 없다").isEqualTo(TrackingHistory.unavailable(DeliveryTrackerChecker.CALL_SLOT_BUSY));
        assertThat(history.state()).isEqualTo(CircuitBreaker.State.HALF_OPEN);
        for (int i = 0; i < 3; i++) {
            assertThat(history.tryAcquirePermission()).as("돌려준 자리 포함 시험 자리 3개가 남아 있다").isTrue();
        }
        clockServer.verify();
    }

    @Test
    @DisplayName("호출 간격 자리를 최대 대기(1초) 안에 못 얻으면 부르지 않고 일시 오류(CALL_SLOT_BUSY)다(주기 작업과 간격을 나눠 씀)")
    void busyCallSlotIsUnavailable() {
        RestClient.Builder spacedBuilder = RestClient.builder();
        MockRestServiceServer spacedServer = MockRestServiceServer.bindTo(spacedBuilder).build();
        DeliveryTrackerChecker spaced = new DeliveryTrackerChecker(new DeliveryTrackerClient(ENABLED, spacedBuilder),
            TrackerCircuitBreaker.create(DeliveryTrackerCircuitBreakerTest.DEFAULTS, Clock.systemUTC()),
            Duration.ofSeconds(5), KST);
        spacedServer.expect(requestTo(URL)).andRespond(withSuccess(events(), MediaType.APPLICATION_JSON));

        assertThat(spaced.history(Delivery.Carrier.CJ, "1").kind()).isEqualTo(DeliveryStatusChecker.HistoryKind.OK);
        long start = System.nanoTime();
        assertThat(spaced.history(Delivery.Carrier.CJ, "2"))
            .isEqualTo(TrackingHistory.unavailable(DeliveryTrackerChecker.CALL_SLOT_BUSY));
        assertThat(Duration.ofNanos(System.nanoTime() - start)).as("기다리지 않고 바로").isLessThan(Duration.ofSeconds(1));
        spacedServer.verify();
    }

    // 오래된 것부터: 집하(+09:00) · 이동(Z) · 배송 출발(장소 없음) · 배송 완료(시각 없음)
    private static String events() {
        return """
            {"data":{"track":{"events":{"edges":[
              {"node":{"time":"2026-11-11T15:00:00+09:00","status":{"code":"AT_PICKUP"},"location":{"name":"서울"}}},
              {"node":{"time":"2026-11-11T09:00:00Z","status":{"code":"IN_TRANSIT"},"location":{"name":"대전HUB"}}},
              {"node":{"time":"2026-11-12T00:30:00.000Z","status":{"code":"OUT_FOR_DELIVERY"},"location":null}},
              {"node":{"status":{"code":"DELIVERED"},"location":{"name":"고객"}}}
            ]}}}}
            """;
    }

    private static String error(String code) {
        return "{\"errors\":[{\"message\":\"\",\"extensions\":{\"code\":\"" + code + "\"}}],\"data\":{\"track\":null}}";
    }

    /**
     * 테스트가 넘기는 시계. 서킷의 열림 유지 판단이 이 시계를 본다.
     */
    private static final class MutableClock extends Clock {

        private Instant now = Instant.parse("2026-11-13T00:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
