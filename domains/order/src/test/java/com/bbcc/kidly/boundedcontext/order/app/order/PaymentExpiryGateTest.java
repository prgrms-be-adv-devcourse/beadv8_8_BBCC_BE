package com.bbcc.kidly.boundedcontext.order.app.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bbcc.kidly.boundedcontext.order.app.port.PaymentEventLag;
import com.bbcc.kidly.boundedcontext.order.domain.expiry.PaymentEventLagCheck;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * 결제 마감 만료 실행 조건(#561, 판정은 #594): 주문 Kafka 수신 스위치가 꺼져 있으면(Spring 수신) 확인 없이 실행,
 * 켜져 있으면 결제 리스너 상태와 상관없이 늘 처리 대기량을 확인해(리스너가 오류로 멈춰도 정상 결제 주문을 취소하지 않게) 대기 0일 때만 실행하고
 * 대기 있음 · 확인 실패 · 배정 전이면 이번 주기를 건너뛴다(INFO — 저절로 맞춰지는 대기라 조치 대상이 아님). 연속으로 건너뛴 시간이 10분을 넘으면 ERROR 한 번, 다시 실행되면 INFO.
 */
@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class PaymentExpiryGateTest {

    private static final Instant START = Instant.parse("2026-10-30T00:00:00Z");

    @Mock
    private PaymentEventLag lag;

    private final MutableClock clock = new MutableClock(START);

    // 스위치 켜짐(Kafka 수신)
    private PaymentExpiryGate gate() {
        return gate(true);
    }

    private PaymentExpiryGate gate(boolean consumerEnabled) {
        return new PaymentExpiryGate(new OrderKafkaConsumerProperties(consumerEnabled), lag, clock);
    }

    @Test
    @DisplayName("수신 스위치가 꺼져 있으면(Spring 수신) 대기량을 확인하지 않고 실행한다")
    void runsWithoutCheckWhenSwitchOff() {
        assertThat(gate(false).allowsRun()).isTrue();
        verifyNoInteractions(lag);
    }

    @Test
    @DisplayName("스위치가 켜져 있으면 결제 리스너 상태와 상관없이 늘 대기량을 확인하고, 대기 0이면 실행한다")
    void runsWhenCaughtUp() {
        when(lag.check()).thenReturn(PaymentEventLagCheck.caughtUp());

        assertThat(gate().allowsRun()).isTrue();
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("skipped")
    @DisplayName("스위치가 켜져 있고 대기 있음 · 확인 실패 · 배정 전이면 건너뛰고 INFO에 이유 코드만 남긴다(WARN 아님)")
    void skips(PaymentEventLagCheck check, String reason, CapturedOutput output) {
        when(lag.check()).thenReturn(check);

        assertThat(gate().allowsRun()).isFalse();
        assertThat(output.getOut()).contains(" INFO ").contains("reason=" + reason).doesNotContain(" WARN ");
    }

    static Stream<Arguments> skipped() {
        return Stream.of(
            Arguments.of(PaymentEventLagCheck.lagging(3), "lag"),
            Arguments.of(PaymentEventLagCheck.checkFailed("timeout"), "check-failed:timeout"),
            Arguments.of(PaymentEventLagCheck.notAssigned(), "not-assigned"));
    }

    @Test
    @DisplayName("연속으로 건너뛴 시간이 10분을 넘는 순간 ERROR 한 번(정확히 10분은 아직, 그 뒤 건너뛰어도 다시 안 찍음)")
    void errorOnceAfterTenMinutes(CapturedOutput output) {
        when(lag.check()).thenReturn(PaymentEventLagCheck.lagging(1));
        PaymentExpiryGate gate = gate();

        gate.allowsRun();
        clock.advance(Duration.ofMinutes(10));
        gate.allowsRun();
        assertThat(errorLines(output)).isZero();

        clock.advance(Duration.ofSeconds(1));
        gate.allowsRun();
        clock.advance(Duration.ofMinutes(1));
        gate.allowsRun();

        assertThat(errorLines(output)).isEqualTo(1);
    }

    @Test
    @DisplayName("다시 실행되면 INFO 한 줄로 알리고 연속 기록을 지운다(새로 건너뛰기 시작하면 다시 10분을 센다)")
    void recoveryResetsStreak(CapturedOutput output) {
        when(lag.check()).thenReturn(PaymentEventLagCheck.lagging(1));
        PaymentExpiryGate gate = gate();
        gate.allowsRun();
        clock.advance(Duration.ofMinutes(11));
        gate.allowsRun();

        when(lag.check()).thenReturn(PaymentEventLagCheck.caughtUp());
        assertThat(gate.allowsRun()).isTrue();
        assertThat(output.getOut()).contains("INFO").contains("결제 마감 만료를 다시 실행");

        when(lag.check()).thenReturn(PaymentEventLagCheck.lagging(1));
        gate.allowsRun();
        clock.advance(Duration.ofMinutes(5));
        gate.allowsRun();
        assertThat(errorLines(output)).isEqualTo(1);
    }


    private static long errorLines(CapturedOutput output) {
        return output.getOut().lines().filter(line -> line.contains(" ERROR ")).count();
    }

    /**
     * 테스트가 시각을 앞으로 옮기는 시계.
     */
    static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("Asia/Seoul");
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
