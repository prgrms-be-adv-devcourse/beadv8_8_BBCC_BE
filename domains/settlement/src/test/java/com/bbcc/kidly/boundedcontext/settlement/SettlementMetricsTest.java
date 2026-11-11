package com.bbcc.kidly.boundedcontext.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.bbcc.kidly.boundedcontext.settlement.app.SettlementMetrics;
import com.bbcc.kidly.boundedcontext.settlement.app.SettlementRunService;
import com.bbcc.kidly.boundedcontext.settlement.app.port.SettlementHolidays;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementRepository;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementStatus;
import com.bbcc.kidly.boundedcontext.settlement.domain.outbox.SettlementOutboxRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SettlementMetricsTest {

    @Mock
    private SettlementOutboxRepository outboxRepository;

    @Mock
    private SettlementRepository settlementRepository;
    @Mock
    private SettlementHolidays holidays;
    @Mock
    private SettlementRunService runService;

    private SimpleMeterRegistry registry;
    private SettlementMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new SettlementMetrics(
            registry, outboxRepository, settlementRepository, holidays, runService, "12:30");
    }

    @Test
    @DisplayName("정산 지연은 영업일 · 마감 뒤 · 미완료일 때만 1이다")
    void runOverdueChecksBusinessDayDeadlineAndCompletion() {
        var now = LocalDateTime.of(2026, 11, 11, 13, 0);
        var holiday = LocalDate.of(2026, 11, 12);
        given(holidays.holidaysIn(Year.of(2026))).willReturn(Set.of(holiday));
        assertThat(metrics.runOverdue(now.withHour(12))).isZero();
        then(runService).should(never()).isCompleted(now.toLocalDate());
        assertThat(metrics.runOverdue(now.withDayOfMonth(14))).isZero();
        assertThat(metrics.runOverdue(now.withDayOfMonth(12))).isZero();
        given(runService.isCompleted(now.toLocalDate())).willReturn(false, true);
        assertThat(metrics.runOverdue(now)).isEqualTo(1);
        assertThat(metrics.runOverdue(now)).isZero();
    }

    @Test
    @DisplayName("공휴일 목록이 없으면 마감 전 · 주말에도 정산 지연은 1이다")
    void missingHolidaysIsOverdueBeforeDeadline() {
        given(holidays.holidaysIn(Year.of(2026))).willReturn(Set.of());
        assertThat(metrics.runOverdue(LocalDateTime.of(2026, 11, 14, 1, 0))).isEqualTo(1);
    }

    @Test
    @DisplayName("미지급 경과 시간은 가장 이른 완료 시각부터 초이고, 없거나 미래면 0이다")
    void oldestUnpaidAgeAndRegisteredGauges() {
        var now = LocalDateTime.of(2026, 11, 11, 13, 0);
        given(settlementRepository.findOldestCompletedAt(SettlementStatus.COMPLETED))
            .willReturn(Optional.of(now.minusSeconds(42)), Optional.of(now.plusSeconds(1)), Optional.empty());
        assertThat(metrics.oldestUnpaidAgeSeconds(now)).isEqualTo(42);
        assertThat(metrics.oldestUnpaidAgeSeconds(now)).isZero();
        assertThat(metrics.oldestUnpaidAgeSeconds(now)).isZero();
        assertThat(registry.find("settlement_run_overdue").gauge()).isNotNull();
        assertThat(registry.find("settlement_payout_oldest_unpaid_age_seconds").gauge()).isNotNull();
    }

    @Test
    @DisplayName("지급 실패 카운터는 기동 때 0으로 등록되고 한 건마다 1 오른다")
    void payoutFailureCounterStartsAtZero() {
        var counter = registry.get("settlement_payout_failed_total").counter();
        assertThat(counter.count()).isZero();
        metrics.countPayoutFailed();
        assertThat(counter.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("안 보낸 아웃박스 게이지는 읽을 때마다 저장소의 건수를 돌려준다")
    void outboxUnsentGauge() {
        given(outboxRepository.countUnsent()).willReturn(3L, 5L);

        assertThat(registry.get("settlement_outbox_unsent").gauge().value()).isEqualTo(3.0);
        assertThat(registry.get("settlement_outbox_unsent").gauge().value()).isEqualTo(5.0);
    }

    @Test
    @DisplayName("아웃박스를 보낸 지연을 타이머에 남긴다")
    void recordSent() {
        metrics.recordSent(Duration.ofSeconds(2));
        metrics.recordSent(Duration.ofSeconds(4));

        var timer = registry.get("settlement_outbox_send_delay_seconds").timer();
        assertThat(timer.count()).isEqualTo(2);
        assertThat(timer.totalTime(TimeUnit.SECONDS)).isEqualTo(6.0);
    }

    @Test
    @DisplayName("지급 재요청은 trigger 태그별로 센다")
    void countRetry() {
        metrics.countRetry(SettlementMetrics.TRIGGER_AUTO);
        metrics.countRetry(SettlementMetrics.TRIGGER_AUTO);
        metrics.countRetry(SettlementMetrics.TRIGGER_ADMIN);

        assertThat(registry.get("settlement_payout_retry_total").tag("trigger", "auto").counter().count())
            .isEqualTo(2.0);
        assertThat(registry.get("settlement_payout_retry_total").tag("trigger", "admin").counter().count())
            .isEqualTo(1.0);
    }

    @Test
    @DisplayName("DLT로 보낸 건수는 원래 토픽 태그별로 센다")
    void countDeadLetter() {
        metrics.countDeadLetter("kidly.order.events");
        metrics.countDeadLetter("kidly.payment.events");
        metrics.countDeadLetter("kidly.payment.events");

        assertThat(registry.get("settlement_dead_letter_total").tag("topic", "kidly.order.events").counter().count())
            .isEqualTo(1.0);
        assertThat(registry.get("settlement_dead_letter_total").tag("topic", "kidly.payment.events").counter().count())
            .isEqualTo(2.0);
    }

    @Test
    @DisplayName("정산서 완료부터 지급 완료까지를 타이머에 남긴다")
    void recordPayout() {
        metrics.recordPayout(Duration.ofMinutes(10));

        var timer = registry.get("settlement_payout_duration_seconds").timer();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(TimeUnit.SECONDS)).isEqualTo(600.0);
    }
}
