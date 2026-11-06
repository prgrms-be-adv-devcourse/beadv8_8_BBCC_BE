package com.bbcc.kidly.boundedcontext.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.notification.app.port.PushFailure;
import com.bbcc.kidly.boundedcontext.notification.app.port.PushSendResult;
import com.bbcc.kidly.boundedcontext.notification.app.port.PushSendResult.Failure;
import com.bbcc.kidly.boundedcontext.notification.app.port.PushSendResult.Outcome;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PushSendResultTest {

    @Test
    @DisplayName("하나라도 받았으면 SENT이고, 구독이 끊긴 FID와 잘못된 FID는 지운다")
    void sentWithInvalidFids() {
        var result = new PushSendResult(1, List.of(
            new Failure("gone", PushFailure.UNREGISTERED),
            new Failure("bad", PushFailure.INVALID_ARGUMENT),
            new Failure("busy", PushFailure.RETRYABLE)));

        assertThat(result.outcome()).isEqualTo(Outcome.SENT);
        assertThat(result.fidsToDelete()).containsExactly("gone", "bad");
    }

    @Test
    @DisplayName("아무 데도 보내지 않았으면(발송을 끈 환경) SKIPPED이고 지울 FID가 없다")
    void notSent() {
        var result = PushSendResult.notSent();

        assertThat(result.outcome()).isEqualTo(Outcome.SKIPPED);
        assertThat(result.fidsToDelete()).isEmpty();
    }

    @Test
    @DisplayName("모든 FID가 INVALID_ARGUMENT면 메시지 문제일 수 있어 지우지 않고 더 시도하지 않는 실패다")
    void allInvalidArgument() {
        var result = new PushSendResult(0, List.of(
            new Failure("a", PushFailure.INVALID_ARGUMENT), new Failure("b", PushFailure.INVALID_ARGUMENT)));

        assertThat(result.outcome()).isEqualTo(Outcome.FAILED);
        assertThat(result.fidsToDelete()).isEmpty();
        assertThat(result.failureReason()).isEqualTo("FCM: 모든 FID가 INVALID_ARGUMENT").doesNotContain("a");
    }

    @Test
    @DisplayName("아무 데도 못 보냈고 일시 오류가 있으면 무효 FID는 지우고 다시 시도할 실패다")
    void retryable() {
        var result = new PushSendResult(0, List.of(
            new Failure("gone", PushFailure.UNREGISTERED), new Failure("busy", PushFailure.RETRYABLE)));

        assertThat(result.outcome()).isEqualTo(Outcome.RETRYABLE_FAILED);
        assertThat(result.fidsToDelete()).containsExactly("gone");
        assertThat(result.failureReason()).isEqualTo("FCM: [RETRYABLE, UNREGISTERED]");
    }

    @Test
    @DisplayName("아무 데도 못 보냈고 그 밖의 거절이면 더 시도하지 않는 실패다")
    void rejected() {
        var result = new PushSendResult(0, List.of(new Failure("a", PushFailure.REJECTED)));

        assertThat(result.outcome()).isEqualTo(Outcome.FAILED);
    }

    @Test
    @DisplayName("모든 FID가 무효(구독 끊김 · 일부 잘못됨)라 지우고 나면 남은 곳이 없어 SKIPPED다")
    void allInvalidated() {
        var result = new PushSendResult(0, List.of(
            new Failure("gone", PushFailure.UNREGISTERED), new Failure("bad", PushFailure.INVALID_ARGUMENT)));

        assertThat(result.outcome()).isEqualTo(Outcome.SKIPPED);
        assertThat(result.fidsToDelete()).containsExactly("gone", "bad");
    }
}
