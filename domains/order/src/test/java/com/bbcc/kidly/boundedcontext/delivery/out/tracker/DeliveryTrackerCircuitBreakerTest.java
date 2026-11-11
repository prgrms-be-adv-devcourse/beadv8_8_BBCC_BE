package com.bbcc.kidly.boundedcontext.delivery.out.tracker;

import static com.bbcc.kidly.boundedcontext.delivery.out.tracker.DeliveryTrackerClientTest.ENABLED;
import static com.bbcc.kidly.boundedcontext.delivery.out.tracker.DeliveryTrackerClientTest.URL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.bbcc.kidly.boundedcontext.delivery.app.port.DeliveryStatusChecker.TrackingResult;
import com.bbcc.kidly.boundedcontext.delivery.domain.Delivery;
import com.bbcc.kidly.boundedcontext.delivery.out.config.DeliveryTrackerCircuitBreakerProperties;
import com.bbcc.kidly.boundedcontext.order.CapturedLogs;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.RestClient;

/**
 * 택배 조회 서킷브레이커(#708). 실제 API를 부르지 않고(가짜 서버) 시간은 주입한 시계를 넘겨 실제로 기다리지 않는다.
 * 기준값은 order.yml 기본값과 같다(창 20 · 최소 10 · 실패율 50 · 느린 호출 5초 · 느린 비율 50 · 열림 1분 · 반열림 3회).
 */
class DeliveryTrackerCircuitBreakerTest {

    static final DeliveryTrackerCircuitBreakerProperties DEFAULTS = new DeliveryTrackerCircuitBreakerProperties(
        20, 10, 50, Duration.ofSeconds(5), 50, Duration.ofMinutes(1), 3);

    private static final String TRACKING_NUMBER = "1234567890";
    private static final int MINIMUM_CALLS = 10;
    private static final int WINDOW = 20;

    private final MutableClock clock = new MutableClock();
    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final TrackerCircuitBreaker circuitBreaker = TrackerCircuitBreaker.create(DEFAULTS, clock);
    private final DeliveryTrackerChecker checker = new DeliveryTrackerChecker(
        new DeliveryTrackerClient(ENABLED, builder), circuitBreaker, Duration.ZERO, ZoneId.of("Asia/Seoul"));

    static Stream<Arguments> countedFailures() {
        return Stream.of(
            Arguments.of("네트워크", withException(new IOException("연결 끊김"))),
            Arguments.of("타임아웃", withException(new SocketTimeoutException("Read timed out"))),
            Arguments.of("5xx", withServerError()),
            Arguments.of("인증 오류", withSuccess(error("UNAUTHENTICATED"), MediaType.APPLICATION_JSON)),
            Arguments.of("호출 한도 초과", withStatus(HttpStatus.TOO_MANY_REQUESTS)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("countedFailures")
    @DisplayName("실패로 세는 오류가 최소 호출 수(10)만큼 이어지면 열리고, 그 뒤는 호출 없이 UNAVAILABLE(CIRCUIT_OPEN)이다")
    void opensOnCountedFailures(String name, ResponseCreator failure) {
        server.expect(times(MINIMUM_CALLS), requestTo(URL)).andRespond(failure);

        List<TrackingResult> failed = check(MINIMUM_CALLS);
        TrackingResult skipped = check();

        assertThat(failed).noneMatch(TrackingResult::isCircuitOpen);
        assertThat(circuitBreaker.state()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(skipped).isEqualTo(TrackingResult.circuitOpen());
        server.verify();
    }

    @Test
    @DisplayName("최소 호출 수 경계: 처음부터 실패 9회는 판단 전이라 닫힘, 10회째에 열린다")
    void minimumCallsBoundary() {
        server.expect(times(MINIMUM_CALLS), requestTo(URL)).andRespond(withServerError());

        check(MINIMUM_CALLS - 1);
        assertThat(circuitBreaker.state()).as("9회").isEqualTo(CircuitBreaker.State.CLOSED);
        check();
        assertThat(circuitBreaker.state()).as("10회").isEqualTo(CircuitBreaker.State.OPEN);
        server.verify();
    }

    @Test
    @DisplayName("실패율 경계(창 20 · 실패율 50): 성공 11 뒤 실패 9(최근 20회 중 9 = 45%)는 닫힘")
    void failureRateBelowThreshold() {
        server.expect(times(11), requestTo(URL)).andRespond(withSuccess(inTransit(), MediaType.APPLICATION_JSON));
        server.expect(times(9), requestTo(URL)).andRespond(withServerError());

        check(WINDOW);

        // 창이 20보다 작았다면(예: 10) 최근 10회 중 실패가 50%를 넘어 이미 열렸다
        assertThat(circuitBreaker.state()).isEqualTo(CircuitBreaker.State.CLOSED);
        server.verify();
    }

    @Test
    @DisplayName("실패율 경계(창 20 · 실패율 50): 성공 10 뒤 실패 10은 20회째(10/20 = 50%)에 처음 열린다")
    void failureRateAtThreshold() {
        server.expect(times(10), requestTo(URL)).andRespond(withSuccess(inTransit(), MediaType.APPLICATION_JSON));
        server.expect(times(10), requestTo(URL)).andRespond(withServerError());

        check(WINDOW - 1);
        assertThat(circuitBreaker.state()).as("19회째(9/19)").isEqualTo(CircuitBreaker.State.CLOSED);
        check();

        assertThat(circuitBreaker.state()).as("20회째(10/20)").isEqualTo(CircuitBreaker.State.OPEN);
        server.verify();
    }

    @Test
    @DisplayName("느린 비율 경계(창 20 · 느린 비율 50): 빠른 11 뒤 느린 9는 닫힘, 빠른 10 뒤 느린 10은 20회째에 열린다")
    void slowCallRateBoundary() {
        server.expect(times(11), requestTo(URL)).andRespond(withSuccess(inTransit(), MediaType.APPLICATION_JSON));
        server.expect(times(9), requestTo(URL)).andRespond(slow());
        check(WINDOW);
        assertThat(circuitBreaker.state()).as("느린 9/20").isEqualTo(CircuitBreaker.State.CLOSED);
        server.verify();
        server.reset();

        TrackerCircuitBreaker atThreshold = TrackerCircuitBreaker.create(DEFAULTS, clock);
        DeliveryTrackerChecker second = new DeliveryTrackerChecker(new DeliveryTrackerClient(ENABLED, builder),
            atThreshold, Duration.ZERO, ZoneId.of("Asia/Seoul"));
        server.expect(times(10), requestTo(URL)).andRespond(withSuccess(inTransit(), MediaType.APPLICATION_JSON));
        server.expect(times(10), requestTo(URL)).andRespond(slow());
        for (int i = 0; i < WINDOW - 1; i++) {
            second.check(Delivery.Carrier.CJ, TRACKING_NUMBER);
        }
        assertThat(atThreshold.state()).as("느린 9/19").isEqualTo(CircuitBreaker.State.CLOSED);
        second.check(Delivery.Carrier.CJ, TRACKING_NUMBER);

        assertThat(atThreshold.state()).as("느린 10/20").isEqualTo(CircuitBreaker.State.OPEN);
        server.verify();
    }

    @Test
    @DisplayName("업무 결과(형식 오류 BAD_REQUEST · 없는 번호 NOT_FOUND)는 실패로 세지 않는다: 실패 9 + 업무 결과 많아도 닫힘")
    void businessResultsNotCounted() {
        server.expect(times(9), requestTo(URL)).andRespond(withServerError());
        server.expect(times(10), requestTo(URL))
            .andRespond(withSuccess(error("NOT_FOUND"), MediaType.APPLICATION_JSON));
        server.expect(times(10), requestTo(URL))
            .andRespond(withSuccess(error("BAD_REQUEST"), MediaType.APPLICATION_JSON));

        check(9);
        assertThat(check(10)).containsOnly(TrackingResult.unavailable("NOT_FOUND"));
        assertThat(check(10)).containsOnly(TrackingResult.invalidTrackingNumber("BAD_REQUEST"));

        assertThat(circuitBreaker.state()).isEqualTo(CircuitBreaker.State.CLOSED);
        server.verify();
    }

    @Test
    @DisplayName("성공이어도 5초를 넘긴 느린 호출이 최소 호출 수만큼이면(느린 비율 50% 이상) 열린다")
    void opensOnSlowCalls() {
        server.expect(times(MINIMUM_CALLS), requestTo(URL)).andRespond(request -> {
            clock.advance(Duration.ofSeconds(6));
            return withSuccess(inTransit(), MediaType.APPLICATION_JSON).createResponse(request);
        });

        assertThat(check(MINIMUM_CALLS)).containsOnly(TrackingResult.inProgress());

        assertThat(circuitBreaker.state()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(check()).isEqualTo(TrackingResult.circuitOpen());
    }

    @Test
    @DisplayName("열림 1분 동안은 부르지 않고, 지나면 반열림으로 3회 시험해 성공이면 닫힌다. 상태가 바뀔 때만 로그 한 줄(열림 WARN, 반열림 · 닫힘 INFO)")
    void halfOpenThenClosed() {
        try (CapturedLogs logs = CapturedLogs.start()) {
            open();
            clock.advance(Duration.ofSeconds(59));
            assertThat(check()).as("열림 유지 중").isEqualTo(TrackingResult.circuitOpen());

            clock.advance(Duration.ofSeconds(2));
            server.expect(times(3), requestTo(URL)).andRespond(withSuccess(inTransit(), MediaType.APPLICATION_JSON));
            assertThat(check(3)).containsOnly(TrackingResult.inProgress());

            assertThat(circuitBreaker.state()).isEqualTo(CircuitBreaker.State.CLOSED);
            server.verify();
            assertThat(breakerLogs(logs)).extracting(ILoggingEvent::getLevel, ILoggingEvent::getFormattedMessage)
                .containsExactly(
                    org.assertj.core.groups.Tuple.tuple(Level.WARN,
                        "택배 조회 서킷 열림: from=CLOSED failureRate=100.0 slowCallRate=0.0 openFor=PT1M"),
                    org.assertj.core.groups.Tuple.tuple(Level.INFO, "택배 조회 서킷 상태: from=OPEN to=HALF_OPEN"),
                    org.assertj.core.groups.Tuple.tuple(Level.INFO, "택배 조회 서킷 상태: from=HALF_OPEN to=CLOSED"));
            assertThat(logs.events()).extracting(ILoggingEvent::getFormattedMessage)
                .noneMatch(message -> message.contains(TRACKING_NUMBER) || message.contains("secret"));
        }
    }

    @Test
    @DisplayName("반열림 시험 호출이 실패하면 다시 열린다(다음 1분은 다시 건너뜀)")
    void halfOpenThenOpenAgain() {
        open();
        clock.advance(Duration.ofMinutes(1).plusSeconds(1));
        server.expect(times(3), requestTo(URL)).andRespond(withServerError());

        check(3);

        assertThat(circuitBreaker.state()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(check()).isEqualTo(TrackingResult.circuitOpen());
        server.verify();
    }

    @Test
    @DisplayName("열린 동안은 호출 간격(200ms)도 기다리지 않아 남은 대상 100건이 바로 끝난다(한 실행이 상한 안에 끝남)")
    void openSkipsWithoutWaiting() {
        TrackerCircuitBreaker breaker = TrackerCircuitBreaker.create(DEFAULTS, clock);
        DeliveryTrackerChecker spaced = new DeliveryTrackerChecker(new DeliveryTrackerClient(ENABLED, builder), breaker,
            Duration.ZERO, ZoneId.of("Asia/Seoul"));
        server.expect(times(MINIMUM_CALLS), requestTo(URL)).andRespond(withServerError());
        for (int i = 0; i < MINIMUM_CALLS; i++) {
            spaced.check(Delivery.Carrier.CJ, TRACKING_NUMBER);
        }
        DeliveryTrackerChecker withInterval = new DeliveryTrackerChecker(new DeliveryTrackerClient(ENABLED, builder),
            breaker, Duration.ofMillis(200), ZoneId.of("Asia/Seoul"));

        long started = System.nanoTime();
        for (int i = 0; i < 100; i++) {
            assertThat(withInterval.check(Delivery.Carrier.CJ, TRACKING_NUMBER))
                .isEqualTo(TrackingResult.circuitOpen());
        }

        // 기다렸다면 100 × 200ms = 20초
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(2));
    }

    // 성공이지만 6초가 걸린 응답(느린 호출 기준 5초 초과). 시계만 넘긴다
    private ResponseCreator slow() {
        return request -> {
            clock.advance(Duration.ofSeconds(6));
            return withSuccess(inTransit(), MediaType.APPLICATION_JSON).createResponse(request);
        };
    }

    @Test
    @DisplayName("서킷 상태 게이지(#1067): 상태마다 한 시계열, 지금 상태만 1. 닫힘 → 열림 → 반열림 시험 중 → 닫힘을 따라간다")
    void circuitStateGauge() {
        MeterRegistry registry = new SimpleMeterRegistry();
        checker.bindCircuitState(registry);
        assertThat(states(registry)).containsExactly(1.0, 0.0, 0.0);

        open();
        assertThat(states(registry)).as("열림").containsExactly(0.0, 1.0, 0.0);

        clock.advance(Duration.ofSeconds(61));
        server.expect(times(3), requestTo(URL)).andRespond(withSuccess(inTransit(), MediaType.APPLICATION_JSON));
        check();
        assertThat(states(registry)).as("반열림 시험 중").containsExactly(0.0, 0.0, 1.0);
        check(2);
        assertThat(states(registry)).as("닫힘").containsExactly(1.0, 0.0, 0.0);
        assertThat(registry.find(DeliveryTrackerChecker.CIRCUIT_STATE).gauges()).hasSize(3);
    }

    // closed · open · half_open 순서의 게이지 값
    private static List<Double> states(MeterRegistry registry) {
        return Stream.of("closed", "open", "half_open")
            .map(state -> registry.get(DeliveryTrackerChecker.CIRCUIT_STATE).tag("state", state).gauge().value())
            .toList();
    }

    private void open() {
        server.expect(times(MINIMUM_CALLS), requestTo(URL)).andRespond(withServerError());
        check(MINIMUM_CALLS);
        assertThat(circuitBreaker.state()).isEqualTo(CircuitBreaker.State.OPEN);
        server.verify();
        server.reset();
    }

    private List<TrackingResult> check(int count) {
        return Stream.generate(this::check).limit(count).toList();
    }

    private TrackingResult check() {
        return checker.check(Delivery.Carrier.CJ, TRACKING_NUMBER);
    }

    private static List<ILoggingEvent> breakerLogs(CapturedLogs logs) {
        return logs.events().stream()
            .filter(event -> event.getLoggerName().equals(TrackerCircuitBreaker.class.getName()))
            .toList();
    }

    private static String inTransit() {
        return "{\"data\":{\"track\":{\"lastEvent\":"
            + "{\"time\":\"2026-11-01T06:00:00.000Z\",\"status\":{\"code\":\"IN_TRANSIT\"}}}}}";
    }

    private static String error(String code) {
        return "{\"errors\":[{\"message\":\"\",\"extensions\":{\"code\":\"" + code + "\"}}],\"data\":{\"track\":null}}";
    }

    /**
     * 테스트가 넘기는 시계. 서킷의 열림 유지 · 느린 호출 판단이 이 시계를 본다.
     */
    private static final class MutableClock extends Clock {

        private Instant now = Instant.parse("2026-11-02T07:00:00Z");

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
