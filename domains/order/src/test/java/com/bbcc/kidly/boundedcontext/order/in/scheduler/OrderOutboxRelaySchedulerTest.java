package com.bbcc.kidly.boundedcontext.order.in.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bbcc.kidly.boundedcontext.order.app.outbox.OrderOutboxProperties;
import com.bbcc.kidly.boundedcontext.order.app.outbox.OrderOutboxRelayService;
import com.bbcc.kidly.boundedcontext.order.app.outbox.dto.OutboxRelayResult;
import com.bbcc.kidly.boundedcontext.order.ImmediateJobRunner;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class OrderOutboxRelaySchedulerTest {

    @Mock
    private OrderOutboxRelayService relayService;

    private OrderOutboxRelayScheduler scheduler;

    private ImmediateJobRunner jobRunner = new ImmediateJobRunner();

    @Test
    @DisplayName("한 서버에서만(#564): 작업 이름은 outbox-relay, 다른 서버가 돌고 있으면(잠금 못 잡음) 아무것도 하지 않는다")
    void singleServer() {
        when(relayService.relay()).thenReturn(new OutboxRelayResult(OutboxRelayResult.Outcome.COMPLETED, 0, 0));
        scheduler.relay();
        assertThat(jobRunner.jobNames()).containsExactly("outbox-relay");

        jobRunner = ImmediateJobRunner.declining();
        setUp();
        scheduler.relay();

        verify(relayService, times(1)).relay();
    }

    @BeforeEach
    void setUp() {
        scheduler = new OrderOutboxRelayScheduler(relayService,
            new OrderOutboxProperties(Duration.ofSeconds(1), 100, Duration.ofDays(7)), jobRunner);
    }

    @Test
    @DisplayName("릴레이가 보냈으면 결과를 INFO로 남긴다")
    void logsSent(CapturedOutput output) {
        when(relayService.relay()).thenReturn(new OutboxRelayResult(OutboxRelayResult.Outcome.COMPLETED, 2, 2));

        scheduler.relay();

        assertThat(output).contains("아웃박스 릴레이: outcome=COMPLETED targets=2 sent=2");
    }

    @Test
    @DisplayName("릴레이가 예외로 끝나도(DB 오류 등) 스케줄러 밖으로 던지지 않고, WARN에는 예외 종류만 남긴다(메시지에 행 값이 있을 수 있음)")
    void swallowsFailureWithoutMessage(CapturedOutput output) {
        when(relayService.relay()).thenThrow(new DataIntegrityViolationException("Failing row contains (비밀 주소)"));

        assertThatCode(() -> scheduler.relay()).doesNotThrowAnyException();

        assertThat(output).contains("아웃박스 릴레이 실패: error=DataIntegrityViolationException");
        assertThat(output).doesNotContain("비밀 주소");
    }
}
