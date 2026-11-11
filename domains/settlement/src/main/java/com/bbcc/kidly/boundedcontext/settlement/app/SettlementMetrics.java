package com.bbcc.kidly.boundedcontext.settlement.app;

import com.bbcc.kidly.boundedcontext.settlement.app.port.SettlementHolidays;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementBusinessDay;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementRepository;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementStatus;
import com.bbcc.kidly.boundedcontext.settlement.domain.outbox.SettlementOutboxRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Year;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 정산 지급이 어디서 멈추는지 보는 Micrometer 지표. 지표 이름 · 태그는 이 클래스에만 둔다(Prometheus에서 소문자 snake_case).
 */
@Component
public class SettlementMetrics {

    public static final String TRIGGER_AUTO = "auto";
    public static final String TRIGGER_ADMIN = "admin";

    private static final String RUN_OVERDUE = "settlement_run_overdue";
    private static final String PAYOUT_OLDEST_UNPAID_AGE = "settlement_payout_oldest_unpaid_age_seconds";
    private static final String PAYOUT_FAILED = "settlement_payout_failed_total";
    private static final String OUTBOX_UNSENT = "settlement_outbox_unsent";
    private static final String OUTBOX_SEND_DELAY = "settlement_outbox_send_delay_seconds";
    private static final String PAYOUT_RETRY = "settlement_payout_retry_total";
    private static final String DEAD_LETTER = "settlement_dead_letter_total";
    private static final String PAYOUT_DURATION = "settlement_payout_duration_seconds";

    private final MeterRegistry registry;
    private final SettlementRepository settlementRepository;
    private final SettlementHolidays holidays;
    private final SettlementRunService runService;
    private final LocalTime runDeadline;
    private final Counter payoutFailed;
    private final Timer outboxSendDelay;
    private final Timer payoutDuration;

    public SettlementMetrics(MeterRegistry registry, SettlementOutboxRepository outboxRepository,
        SettlementRepository settlementRepository, SettlementHolidays holidays, SettlementRunService runService,
        @Value("${kidly.settlement.run-deadline}") String runDeadline) {
        this.registry = registry;
        this.settlementRepository = settlementRepository;
        this.holidays = holidays;
        this.runService = runService;
        this.runDeadline = LocalTime.parse(runDeadline);
        Gauge.builder(RUN_OVERDUE, () -> runOverdue(LocalDateTime.now()))
            .description("영업일 마감 시각까지 오늘 정산 실행이 끝나지 않았거나 영업일을 판단하지 못하면 1")
            .register(registry);
        Gauge.builder(PAYOUT_OLDEST_UNPAID_AGE, () -> oldestUnpaidAgeSeconds(LocalDateTime.now()))
            .description("지급을 요청하고 결과를 받지 못한 정산서 중 가장 오래된 것의 경과 시간(초). 없으면 0")
            .register(registry);
        // 기동 때 0으로 등록해 둔다. 처음 실패할 때 등록하면 increase()가 그 1건을 놓친다
        this.payoutFailed = Counter.builder(PAYOUT_FAILED)
            .description("지급 실패로 바뀐 정산서 수")
            .register(registry);
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

    public double runOverdue(LocalDateTime now) {
        var today = now.toLocalDate();
        // 캘린더는 공휴일 목록이 없을 때마다 ERROR를 기록하므로 직접 조회한다.
        var dates = holidays.holidaysIn(Year.from(today));
        if (dates.isEmpty()) {
            return 1;
        }
        if (!SettlementBusinessDay.isBusinessDay(today, dates) || now.toLocalTime().isBefore(runDeadline)) {
            return 0;
        }
        return runService.isCompleted(today) ? 0 : 1;
    }

    public double oldestUnpaidAgeSeconds(LocalDateTime now) {
        return settlementRepository.findOldestCompletedAt(SettlementStatus.COMPLETED)
            .map(at -> (double) Math.max(0, Duration.between(at, now).toSeconds()))
            .orElse(0.0);
    }

    public void countPayoutFailed() {
        payoutFailed.increment();
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
