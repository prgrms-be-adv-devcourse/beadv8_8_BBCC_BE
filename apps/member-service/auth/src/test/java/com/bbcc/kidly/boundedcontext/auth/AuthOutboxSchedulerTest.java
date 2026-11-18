package com.bbcc.kidly.boundedcontext.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.willThrow;

import com.bbcc.kidly.boundedcontext.auth.app.outbox.AuthOutboxCleanupService;
import com.bbcc.kidly.boundedcontext.auth.app.outbox.AuthOutboxRelayService;
import com.bbcc.kidly.boundedcontext.auth.in.outbox.AuthOutboxScheduler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class AuthOutboxSchedulerTest {

    @Mock
    private AuthOutboxRelayService relayService;

    @Mock
    private AuthOutboxCleanupService cleanupService;

    @Test
    @DisplayName("정리가 예외로 끝나도 스케줄러 밖으로 던지지 않고, 로그에는 예외 종류만 남긴다")
    void cleanUpKeepsExceptionMessageOutOfLogs(CapturedOutput output) {
        willThrow(new DataIntegrityViolationException("Failing row contains (계정 1)"))
            .given(cleanupService).cleanUp();
        var scheduler = new AuthOutboxScheduler(relayService, cleanupService);

        assertThatCode(scheduler::cleanUp).doesNotThrowAnyException();

        assertThat(output.getAll()).contains("WARN").contains("DataIntegrityViolationException")
            .doesNotContain("계정 1");
    }
}
