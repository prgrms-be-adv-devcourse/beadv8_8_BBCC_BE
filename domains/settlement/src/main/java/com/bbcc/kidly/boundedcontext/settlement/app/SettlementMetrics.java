package com.bbcc.kidly.boundedcontext.settlement.app;

import com.bbcc.kidly.boundedcontext.settlement.domain.outbox.SettlementOutboxRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * 정산 지급이 어디서 멈추는지 보는 Micrometer 지표. 지표 이름 · 태그는 이 클래스에만 둔다(Prometheus에서 소문자 snake_case).
 */
@Component
public class SettlementMetrics {

    public static final String TRIGGER_AUTO = "auto";
    public static final String TRIGGER_ADMIN = "admin";

    private static final String OUTBOX_UNSENT = "settlement_outbox_unsent";
    private static final String OUTBOX_SEND_DELAY = "settlement_outbox_send_delay_seconds";
    private static final String PAYOUT_RETRY = "settlement_payout_retry_total";
    private static final String DEAD_LETTER = "settlement_dead_letter_total";
    private static final String PAYOUT_DURATION = "settlement_payout_duration_seconds";

    private final MeterRegistry registry;
    private final Timer outboxSendDelay;
    private final Timer payoutDuration;

    public SettlementMetrics(MeterRegistry registry, SettlementOutboxRepository outboxRepository) {
        this.registry = registry;
        // 수집 주기마다 count 쿼리를 한 번 읽는다
        Gauge.builder(OUTBOX_UNSENT, outboxRepository, SettlementOutboxRepository::countUnsent)
            .description("아직 Kafka로 보내지 않은 정산 아웃박스 행 수")
            .register(registry);
        this.outboxSendDelay = Timer.builder(OUTBOX_SEND_DELAY)
            .description("아웃박스 행 저장부터 Kafka로 보낸 시각까지")
            .register(registry);
        this.payoutDuration = Timer.builder(PAYOUT_DURATION)
            .description("정산서 완료부터 지급 완료까지")
            .register(registry);
    }

    public void recordSent(Duration delay) {
        outboxSendDelay.record(delay);
    }

    public void countRetry(String trigger) {
        Counter.builder(PAYOUT_RETRY)
            .description("정산 지급 재요청 건수")
            .tag("trigger", trigger)
            .register(registry)
            .increment();
    }

    public void countDeadLetter(String topic) {
        Counter.builder(DEAD_LETTER)
            .description("DLT로 보낸 정산 이벤트 건수")
            .tag("topic", topic)
            .register(registry)
            .increment();
    }

    public void recordPayout(Duration duration) {
        payoutDuration.record(duration);
    }
}
