package com.bbcc.kidly.boundedcontext.order.in.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bbcc.kidly.boundedcontext.order.ImmediateJobRunner;
import com.bbcc.kidly.boundedcontext.order.app.outbox.OrderOutboxCleanupService;
import com.bbcc.kidly.boundedcontext.order.app.outbox.OrderOutboxProperties;
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
class OrderOutboxCleanupSchedulerTest {

    @Mock
    private OrderOutboxCleanupService cleanupService;

    private OrderOutboxCleanupScheduler scheduler;

    private ImmediateJobRunner jobRunner = new ImmediateJobRunner();

    @Test
    @DisplayName("한 서버에서만(#564): 작업 이름은 outbox-cleanup, 다른 서버가 돌고 있으면(잠금 못 잡음) 아무것도 하지 않는다")
    void singleServer() {
        scheduler.cleanUp();
        assertThat(jobRunner.jobNames()).containsExactly("outbox-cleanup");

        jobRunner = ImmediateJobRunner.declining();
        setUp();
        scheduler.cleanUp();

        verify(cleanupService, times(1)).cleanUp();
    }

    @BeforeEach
    void setUp() {
        scheduler = new OrderOutboxCleanupScheduler(cleanupService,
            new OrderOutboxProperties(Duration.ofSeconds(1), 100, Duration.ofDays(7)), jobRunner);
    }

    @Test
    @DisplayName("정리는 보관 기간이 지난 보낸 메시지를 지우고 건수를 남긴다")
    void cleansUp(CapturedOutput output) {
        when(cleanupService.cleanUp()).thenReturn(3);

        scheduler.cleanUp();

        assertThat(output).contains("아웃박스 정리: deleted=3 retention=PT168H");
    }

    @Test
    @DisplayName("정리가 예외로 끝나도 스케줄러 밖으로 던지지 않고, WARN에는 예외 종류만 남긴다")
    void swallowsFailureWithoutMessage(CapturedOutput output) {
        when(cleanupService.cleanUp()).thenThrow(new DataIntegrityViolationException("Failing row contains (비밀 주소)"));

        assertThatCode(() -> scheduler.cleanUp()).doesNotThrowAnyException();

        assertThat(output).contains("아웃박스 정리 실패: error=DataIntegrityViolationException");
        assertThat(output).doesNotContain("비밀 주소");
    }
}
