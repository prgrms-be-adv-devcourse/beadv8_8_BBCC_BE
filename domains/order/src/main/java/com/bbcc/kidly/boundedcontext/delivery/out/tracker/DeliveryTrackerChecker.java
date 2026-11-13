package com.bbcc.kidly.boundedcontext.delivery.out.tracker;

import com.bbcc.kidly.boundedcontext.delivery.app.port.DeliveryStatusChecker;
import com.bbcc.kidly.boundedcontext.delivery.app.port.DeliveryStatusChecker.TrackingEvent;
import com.bbcc.kidly.boundedcontext.delivery.app.port.DeliveryStatusChecker.TrackingHistory;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;
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

    /**
     * 구매자 진행 이력(#1320)이 호출 간격 자리를 기다리는 최대 시간. 넘으면 부르지 않고 일시 오류(요청 스레드를 오래 잡지 않게).
     */
    static final Duration HISTORY_MAX_WAIT = Duration.ofSeconds(1);
    static final String CALL_SLOT_BUSY = "CALL_SLOT_BUSY";

    private final DeliveryTrackerClient client;
    private final TrackerCircuitBreaker circuitBreaker;
    private final TrackerCircuitBreaker historyCircuitBreaker;
    private final long minCallIntervalNanos;
    private final long historyMaxWaitNanos;
    private final ZoneId zone;
    // 호출 간격은 상태 조회(주기 작업)와 구매자 진행 이력이 함께 나눠 쓴다(호출 한도는 Delivery Tracker 계정 하나)
    private final ReentrantLock callIntervalLock = new ReentrantLock(true);
    private long lastCallNanos;

    DeliveryTrackerChecker(DeliveryTrackerClient client, TrackerCircuitBreaker circuitBreaker, Duration minCallInterval,
        ZoneId zone) {
        this(client, circuitBreaker, circuitBreaker.sibling("delivery-tracker-history", "택배 진행 이력 서킷"),
            minCallInterval, HISTORY_MAX_WAIT, zone);
    }

    DeliveryTrackerChecker(DeliveryTrackerClient client, TrackerCircuitBreaker circuitBreaker,
        TrackerCircuitBreaker historyCircuitBreaker, Duration minCallInterval, Duration historyMaxWait, ZoneId zone) {
        this.client = client;
        this.circuitBreaker = circuitBreaker;
        this.historyCircuitBreaker = historyCircuitBreaker;
        this.minCallIntervalNanos = minCallInterval.toNanos();
        this.historyMaxWaitNanos = historyMaxWait.toNanos();
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

    /**
     * 진행 이력(#1320, 구매자 요청). 서킷은 상태 조회와 따로 센다(같은 설정, 구매자 조회 실패가 배송 완료 반영을 막지 않게).
     * 호출 간격 자리는 상태 조회와 나눠 쓰되 최대 1초만 기다리고 못 얻으면 부르지 않고 일시 오류(CALL_SLOT_BUSY)다.
     * 테스트용 DUMMY는 구매자에게 보여 줄 이력이 아니라 조회하지 않는다. 택배사에 아직 등록 전(NOT_FOUND)은 오류가 아니라 빈 이력이다
     * (발송 직후 흔하다, {@link TrackingHistory#notRegistered()}). 이벤트는 최신 먼저로 뒤집는다.
     */
    @Override
    public TrackingHistory history(Delivery.Carrier carrier, String trackingNumber) {
        String carrierId = carrier == Delivery.Carrier.DUMMY ? null : CARRIER_IDS.get(carrier);
        if (carrierId == null) {
            return TrackingHistory.unsupportedCarrier();
        }
        // 꺼져 있으면(인증값 없음) 부르지 않는다. 서킷에 실패로 세지 않게 호출 전에 거른다
        if (!client.isEnabled()) {
            return TrackingHistory.unavailable("DISABLED");
        }
        // 서킷 허락을 먼저 받는다: 열림 → 반열림 전환은 허락을 물을 때만 일어나므로 열렸는지 미리 보고 거르면 다시 닫히지 않는다.
        // 열려 있으면 호출 간격 자리를 쓰지 않는다
        if (!historyCircuitBreaker.tryAcquirePermission()) {
            return TrackingHistory.unavailable(TrackingResult.CIRCUIT_OPEN);
        }
        if (!tryWaitForCallInterval(historyMaxWaitNanos)) {
            historyCircuitBreaker.releasePermission();
            return TrackingHistory.unavailable(CALL_SLOT_BUSY);
        }
        try {
            List<TrackEventRow> rows = historyCircuitBreaker.callPermitted(
                () -> client.events(carrierId, trackingNumber));
            return TrackingHistory.ok(rows.reversed().stream()
                .map(row -> new TrackingEvent(
                    row.time() == null ? null : row.time().atZoneSameInstant(zone).toLocalDateTime(),
                    row.location(), row.statusCode()))
                .toList());
        } catch (DeliveryTrackerException e) {
            if ("NOT_FOUND".equals(e.code())) {
                return TrackingHistory.notRegistered();
            }
            return e.isInvalidTrackingNumber()
                ? TrackingHistory.invalidTrackingNumber(e.code())
                : TrackingHistory.unavailable(e.code());
        }
    }

    // 호출 한도(초당 10회)를 넘지 않게 호출 사이를 띄운다. 스케줄러 한 스레드가 부르지만 다른 호출이 생겨도 지켜지게 동기화한다
    private void waitForCallInterval() {
        callIntervalLock.lock();
        try {
            long waitNanos = lastCallNanos + minCallIntervalNanos - System.nanoTime();
            if (lastCallNanos != 0 && waitNanos > 0) {
                LockSupport.parkNanos(waitNanos);
            }
            lastCallNanos = System.nanoTime();
        } finally {
            callIntervalLock.unlock();
        }
    }

    // 구매자 요청용: 자리(잠금 + 간격)를 maxWaitNanos 안에 얻을 때만 true. 주기 작업 경로(waitForCallInterval)는 그대로 기다린다
    private boolean tryWaitForCallInterval(long maxWaitNanos) {
        long deadline = System.nanoTime() + maxWaitNanos;
        try {
            if (!callIntervalLock.tryLock(maxWaitNanos, TimeUnit.NANOSECONDS)) {
                return false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        try {
            long now = System.nanoTime();
            long waitNanos = lastCallNanos + minCallIntervalNanos - now;
            if (lastCallNanos != 0 && waitNanos > 0) {
                if (waitNanos > deadline - now) {
                    return false;
                }
                LockSupport.parkNanos(waitNanos);
            }
            lastCallNanos = System.nanoTime();
            return true;
        } finally {
            callIntervalLock.unlock();
        }
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
