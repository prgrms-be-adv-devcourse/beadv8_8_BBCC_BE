package com.bbcc.kidly.boundedcontext.delivery.out.tracker;

import com.bbcc.kidly.boundedcontext.delivery.app.port.DeliveryStatusChecker;
import com.bbcc.kidly.boundedcontext.delivery.app.port.DeliveryStatusChecker.TrackingResult;
import com.bbcc.kidly.boundedcontext.delivery.domain.Delivery;
import com.bbcc.kidly.boundedcontext.delivery.out.config.DeliveryTrackerCircuitBreakerProperties;
import com.bbcc.kidly.boundedcontext.delivery.out.config.DeliveryTrackerProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.locks.LockSupport;
import org.springframework.web.client.RestClient;

/**
 * DeliveryStatusChecker의 Delivery Tracker 구현. 택배사 enum → Delivery Tracker 택배사 ID 변환,
 * 응답 → TrackingResult 변환, 호출 간격 유지(호출 한도 초당 10회), 서킷브레이커(#708)를 맡는다. 외부 서비스 ID는 domain에 두지 않는다.
 * 서킷이 열려 있으면 부르지 않고(호출 간격도 기다리지 않음) UNAVAILABLE(사유 CIRCUIT_OPEN)을 돌려준다: 기존 일시 오류처럼 다음 실행에서
 * 다시 조회한다.
 */
public class DeliveryTrackerChecker implements DeliveryStatusChecker {

    static final String CIRCUIT_STATE = "kidly.order.delivery.tracker.circuit.state";
    private static final List<CircuitBreaker.State> CIRCUIT_STATES = List.of(CircuitBreaker.State.CLOSED,
        CircuitBreaker.State.OPEN, CircuitBreaker.State.HALF_OPEN);

    // 10/15 실제 API carriers 목록으로 확인한 ID
    static final Map<Delivery.Carrier, String> CARRIER_IDS = new EnumMap<>(Map.of(
        Delivery.Carrier.CJ, "kr.cjlogistics",
        Delivery.Carrier.EPOST, "kr.epost",
        Delivery.Carrier.HANJIN, "kr.hanjin",
        Delivery.Carrier.LOTTE, "kr.lotte",
        Delivery.Carrier.LOGEN, "kr.logen",
        Delivery.Carrier.DUMMY, "dev.track.dummy"));

    private static final String DELIVERED = "DELIVERED";

    private final DeliveryTrackerClient client;
    private final TrackerCircuitBreaker circuitBreaker;
    private final long minCallIntervalNanos;
    private final ZoneId zone;
    private long lastCallNanos;

    DeliveryTrackerChecker(DeliveryTrackerClient client, TrackerCircuitBreaker circuitBreaker, Duration minCallInterval,
        ZoneId zone) {
        this.client = client;
        this.circuitBreaker = circuitBreaker;
        this.minCallIntervalNanos = minCallInterval.toNanos();
        this.zone = zone;
    }

    /**
     * 빈 설정(out/config의 DeliveryTrackerConfig, #651)이 쓴다. 클라이언트는 받은 빌더를 그대로 써서 테스트에서 가짜 서버를 붙일 수 있다.
     */
    public static DeliveryTrackerChecker create(DeliveryTrackerProperties properties,
        DeliveryTrackerCircuitBreakerProperties circuitBreaker, RestClient.Builder builder, ZoneId zone, Clock clock) {
        return new DeliveryTrackerChecker(new DeliveryTrackerClient(properties, builder),
            TrackerCircuitBreaker.create(circuitBreaker, clock), properties.minCallInterval(), zone);
    }

    @Override
    public boolean isEnabled() {
        return client.isEnabled();
    }

    @Override
    public TrackingResult check(Delivery.Carrier carrier, String trackingNumber) {
        try {
            TrackResult result = circuitBreaker.callIfPermitted(this::waitForCallInterval,
                () -> client.track(CARRIER_IDS.get(carrier), trackingNumber));
            if (result == null) {
                return TrackingResult.circuitOpen();
            }
            if (!DELIVERED.equals(result.statusCode())) {
                return TrackingResult.inProgress();
            }
            // 택배사마다 오프셋이 달라(Z · +09:00) 같은 순간의 서버 시간대 시각으로 바꿔 shippedAt(LocalDateTime)과 비교한다
            return TrackingResult.delivered(result.eventTime().atZoneSameInstant(zone).toLocalDateTime());
        } catch (DeliveryTrackerException e) {
            return e.isInvalidTrackingNumber()
                ? TrackingResult.invalidTrackingNumber(e.code())
                : TrackingResult.unavailable(e.code());
        }
    }

    // 호출 한도(초당 10회)를 넘지 않게 호출 사이를 띄운다. 스케줄러 한 스레드가 부르지만 다른 호출이 생겨도 지켜지게 동기화한다
    private synchronized void waitForCallInterval() {
        long waitNanos = lastCallNanos + minCallIntervalNanos - System.nanoTime();
        if (lastCallNanos != 0 && waitNanos > 0) {
            LockSupport.parkNanos(waitNanos);
        }
        lastCallNanos = System.nanoTime();
    }

    /**
     * 서킷 상태 게이지(#1067)를 등록한다: kidly.order.delivery.tracker.circuit.state{state=closed|open|half_open}, 지금 상태만 1이고
     * 나머지는 0. 열림 뒤 반열림은 다음 호출을 허락받을 때 바뀐다(그 전까지 open).
     */
    public void bindCircuitState(MeterRegistry registry) {
        for (CircuitBreaker.State state : CIRCUIT_STATES) {
            Gauge.builder(CIRCUIT_STATE, circuitBreaker, breaker -> breaker.state() == state ? 1 : 0)
                .description("택배 조회 서킷브레이커 상태. 지금 상태(closed · open · half_open)만 1")
                .tag("state", state.name().toLowerCase(Locale.ROOT))
                .register(registry);
        }
    }
}
