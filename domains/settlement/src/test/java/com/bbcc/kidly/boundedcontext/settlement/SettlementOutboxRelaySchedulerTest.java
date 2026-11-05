package com.bbcc.kidly.boundedcontext.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.bbcc.kidly.boundedcontext.settlement.app.outbox.SettlementOutboxProperties;
import com.bbcc.kidly.boundedcontext.settlement.app.outbox.SettlementOutboxRelayResult;
import com.bbcc.kidly.boundedcontext.settlement.app.outbox.SettlementOutboxRelayResult.Outcome;
import com.bbcc.kidly.boundedcontext.settlement.app.outbox.SettlementOutboxRelayService;
import com.bbcc.kidly.boundedcontext.settlement.in.outbox.SettlementOutboxRelayScheduler;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class SettlementOutboxRelaySchedulerTest {

    private static final Instant T0 = Instant.parse("2026-11-04T09:00:00Z");
    private static final String ALERT = "정산 아웃박스를 5분 넘게 보내지 못하고 있습니다";
    private static final SettlementOutboxRelayResult STOPPED = new SettlementOutboxRelayResult(Outcome.STOPPED, 3, 0);
    private static final SettlementOutboxRelayResult COMPLETED =
        new SettlementOutboxRelayResult(Outcome.COMPLETED, 3, 3);

    @Mock
    private SettlementOutboxRelayService relayService;

    @Mock
    private SettlementOutboxProperties properties;

    @InjectMocks
    private SettlementOutboxRelayScheduler scheduler;

    private CapturedOutput output;

    @BeforeEach
    void capture(CapturedOutput capturedOutput) {
        this.output = capturedOutput;
    }

    @Test
    @DisplayName("5분 넘게 계속 보내지 못하면 ERROR를 남기고, 그 뒤로는 5분에 한 번만 남긴다")
    void alertsAfterFiveMinutesOfFailure() {
        given(relayService.relay()).willReturn(STOPPED);

        scheduler.relay(T0);
        scheduler.relay(T0.plus(Duration.ofMinutes(4)));
        scheduler.relay(T0.plus(Duration.ofMinutes(5)));
        scheduler.relay(T0.plus(Duration.ofMinutes(6)));
        scheduler.relay(T0.plus(Duration.ofMinutes(10)));

        assertThat(countAlerts()).isEqualTo(2);
    }

    @Test
    @DisplayName("보내기에 성공하면 실패 시작 시각을 비워 다시 5분을 센다")
    void resetsOnSuccess() {
        given(relayService.relay()).willReturn(STOPPED, STOPPED, COMPLETED, STOPPED, STOPPED);

        scheduler.relay(T0);
        scheduler.relay(T0.plus(Duration.ofMinutes(4)));
        scheduler.relay(T0.plus(Duration.ofMinutes(4)));
        scheduler.relay(T0.plus(Duration.ofMinutes(5)));
        scheduler.relay(T0.plus(Duration.ofMinutes(9)));

        assertThat(countAlerts()).isZero();
    }

    @Test
    @DisplayName("예외로 끝나는 실패도 같은 기준으로 센다")
    void countsExceptionsAsFailure() {
        given(relayService.relay()).willThrow(new IllegalStateException("db down"));

        scheduler.relay(T0);
        scheduler.relay(T0.plus(Duration.ofMinutes(5)));

        assertThat(countAlerts()).isEqualTo(1);
    }

    private long countAlerts() {
        return output.toString().lines().filter(line -> line.contains(ALERT)).count();
    }
}
