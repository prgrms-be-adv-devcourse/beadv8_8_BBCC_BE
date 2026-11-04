package com.bbcc.kidly.boundedcontext.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.bbcc.kidly.boundedcontext.settlement.app.SettlementMetrics;
import com.bbcc.kidly.boundedcontext.settlement.domain.outbox.SettlementOutboxRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
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

    private SimpleMeterRegistry registry;
    private SettlementMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new SettlementMetrics(registry, outboxRepository);
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
