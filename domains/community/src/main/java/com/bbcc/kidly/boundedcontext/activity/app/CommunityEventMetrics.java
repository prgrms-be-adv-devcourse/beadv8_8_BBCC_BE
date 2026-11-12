package com.bbcc.kidly.boundedcontext.activity.app;

import com.bbcc.kidly.boundedcontext.activity.app.port.EventOutbox;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 커뮤니티 이벤트 아웃박스 · 릴레이 지표(이름 · 라벨 값은 이 클래스에만, 라벨은 고정 집합).
 * <ul>
 *   <li>{@code kidly.community.outbox.messages{state=pending|quarantined}}: 대기 · 격리 행 수</li>
 *   <li>{@code kidly.community.outbox.oldest.age.seconds}: 가장 오래된 대기 행 나이(초), 없으면 0</li>
 *   <li>{@code kidly.community.outbox.send.failures}: 보내기 실패 횟수</li>
 *   <li>{@code kidly.community.scheduler.last.success.seconds{task=event_relay}}: 릴레이가 끝까지 돈 마지막 시각(epoch 초). 켜진
 *       릴레이만 등록하고 처음 값은 등록(기동) 시각. 건너뛴 주기 · 예외는 갱신하지 않는다</li>
 * </ul>
 * 게이지 조회가 실패하면 NaN(수집을 깨지 않는다).
 */
@Slf4j
@Component
public class CommunityEventMetrics {

    public static final String OUTBOX_MESSAGES = "kidly.community.outbox.messages";
    public static final String OUTBOX_OLDEST_AGE = "kidly.community.outbox.oldest.age.seconds";
    public static final String OUTBOX_SEND_FAILURES = "kidly.community.outbox.send.failures";
    public static final String SCHEDULER_LAST_SUCCESS = "kidly.community.scheduler.last.success.seconds";
    public static final String RELAY_TASK = "event_relay";

    private final MeterRegistry registry;
    private final Clock clock;
    private final Counter sendFailures;
    private final AtomicReference<Double> relayLastSuccess = new AtomicReference<>();

    @Autowired
    public CommunityEventMetrics(MeterRegistry registry, EventOutbox outbox) {
        this(registry, outbox, Clock.systemDefaultZone());
    }

    public CommunityEventMetrics(MeterRegistry registry, EventOutbox outbox, Clock clock) {
        this.registry = registry;
        this.clock = clock;
        Gauge.builder(OUTBOX_MESSAGES, this, metrics -> metrics.read(() -> (double) outbox.countPending()))
            .tag("state", "pending").description("커뮤니티 이벤트 아웃박스 대기 행 수").register(registry);
        Gauge.builder(OUTBOX_MESSAGES, this, metrics -> metrics.read(() -> (double) outbox.countQuarantined()))
            .tag("state", "quarantined").description("커뮤니티 이벤트 아웃박스 격리 행 수").register(registry);
        Gauge.builder(OUTBOX_OLDEST_AGE, this,
                metrics -> metrics.read(() -> metrics.ageOrZero(outbox.findOldestPendingCreatedAt())))
            .description("가장 오래된 대기 행 나이(초), 없으면 0").register(registry);
        sendFailures = Counter.builder(OUTBOX_SEND_FAILURES).description("커뮤니티 이벤트 보내기 실패 횟수")
            .register(registry);
    }

    void sendFailed() {
        sendFailures.increment();
    }

    /**
     * 켜진 릴레이를 등록한다(처음 값은 지금 = 기동 시각). 릴레이가 꺼지면 부르지 않으므로 시계열이 없다.
     */
    public synchronized void registerRelay() {
        if (relayLastSuccess.get() != null) {
            return;
        }
        relayLastSuccess.set(nowSeconds());
        Gauge.builder(SCHEDULER_LAST_SUCCESS, relayLastSuccess, AtomicReference::get).tag("task", RELAY_TASK)
            .description("그 스케줄러가 이 서버에서 끝까지 돈 마지막 시각(epoch 초), 처음 값은 기동 시각")
            .register(registry);
    }

    /**
     * 릴레이가 한 주기를 끝까지 돌았다(건너뛴 주기 · 예외는 부르지 않는다). 등록 전이면 무시한다.
     */
    public void recordRelaySuccess() {
        if (relayLastSuccess.get() != null) {
            relayLastSuccess.set(nowSeconds());
        }
    }

    private double read(Supplier<Double> value) {
        try {
            return value.get();
        } catch (RuntimeException e) {
            log.warn("커뮤니티 이벤트 지표를 읽지 못했습니다. error={}", e.getClass().getSimpleName());
            return Double.NaN;
        }
    }

    private double ageOrZero(LocalDateTime since) {
        if (since == null) {
            return 0;
        }
        double age = Duration.between(since.atZone(clock.getZone()).toInstant(), clock.instant()).toMillis() / 1000.0;
        return Math.max(0, age);
    }

    private double nowSeconds() {
        return clock.instant().toEpochMilli() / 1000.0;
    }
}
