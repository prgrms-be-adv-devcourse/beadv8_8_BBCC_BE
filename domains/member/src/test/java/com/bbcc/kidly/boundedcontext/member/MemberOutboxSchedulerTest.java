package com.bbcc.kidly.boundedcontext.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.willThrow;

import com.bbcc.kidly.boundedcontext.member.app.outbox.MemberOutboxCleanupService;
import com.bbcc.kidly.boundedcontext.member.app.outbox.MemberOutboxRelayService;
import com.bbcc.kidly.boundedcontext.member.in.outbox.MemberOutboxScheduler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class MemberOutboxSchedulerTest {

    @Mock
    private MemberOutboxRelayService relayService;

    @Mock
    private MemberOutboxCleanupService cleanupService;

    @Test
    @DisplayName("릴레이가 DB 오류 같은 예외로 끝나도 스케줄러 밖으로 던지지 않고, 로그에는 예외 종류만 남긴다")
    void relayKeepsRowValuesOutOfLogs(CapturedOutput output) {
        // DB 오류 메시지에는 행 값(본문)이 들어갈 수 있다
        willThrow(new DataIntegrityViolationException("Failing row contains (회원 1)"))
            .given(relayService).relay();
        var scheduler = new MemberOutboxScheduler(relayService, cleanupService);

        assertThatCode(scheduler::relay).doesNotThrowAnyException();

        assertThat(output.getAll()).contains("WARN").contains("DataIntegrityViolationException")
            .doesNotContain("회원 1");
    }

    @Test
    @DisplayName("정리가 예외로 끝나도 스케줄러 밖으로 던지지 않고, 로그에는 예외 종류만 남긴다")
    void cleanUpKeepsExceptionMessageOutOfLogs(CapturedOutput output) {
        willThrow(new DataIntegrityViolationException("Failing row contains (회원 1)"))
            .given(cleanupService).cleanUp();
        var scheduler = new MemberOutboxScheduler(relayService, cleanupService);

        assertThatCode(scheduler::cleanUp).doesNotThrowAnyException();

        assertThat(output.getAll()).contains("WARN").contains("DataIntegrityViolationException")
            .doesNotContain("회원 1");
    }
}
