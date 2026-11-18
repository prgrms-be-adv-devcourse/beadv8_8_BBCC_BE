package com.bbcc.kidly.boundedcontext.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.bbcc.kidly.boundedcontext.auth.app.outbox.AuthOutboxRelayService;
import com.bbcc.kidly.boundedcontext.auth.app.port.outbox.AuthOutboxSendException;
import com.bbcc.kidly.boundedcontext.auth.app.port.outbox.AuthOutboxSender;
import com.bbcc.kidly.boundedcontext.auth.domain.outbox.AuthOutboxMessage;
import com.bbcc.kidly.boundedcontext.auth.domain.outbox.AuthOutboxRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class AuthOutboxRelayServiceTest {

    @Mock
    private AuthOutboxRepository repository;

    @Mock
    private AuthOutboxSender sender;

    @Test
    @DisplayName("잠금을 얻은 뒤 보내지 않은 메시지를 받은 순서대로 보내고, 보낸 메시지마다 보낸 시각과 시도 1회를 남긴다")
    void sendInOrderAfterLock() {
        var first = message();
        var second = message();
        given(repository.tryLockRelay()).willReturn(true);
        given(repository.findUnsentCreatedBefore(any(), eq(100))).willReturn(List.of(first, second));

        relayService().relay();

        var order = inOrder(repository, sender);
        order.verify(repository).tryLockRelay();
        order.verify(sender).send(first);
        order.verify(sender).send(second);
        assertThat(List.of(first, second)).allSatisfy(message -> {
            assertThat(message.getSentAt()).isNotNull();
            assertThat(message.getAttemptCount()).isEqualTo(1);
        });
    }

    @Test
    @DisplayName("다른 릴레이가 잠금을 쥐고 있으면 메시지를 읽지도 보내지도 않는다")
    void skipWhenLocked() {
        given(repository.tryLockRelay()).willReturn(false);

        relayService().relay();

        verify(repository, never()).findUnsentCreatedBefore(any(), anyInt());
        verifyNoInteractions(sender);
    }

    @Test
    @DisplayName("보내다 실패하면 그 메시지에 시도 1회와 오류를 남기고 멈춘다. 로그에는 예외 메시지만 남기고 본문은 남기지 않는다")
    void recordFailureAndStop(CapturedOutput output) {
        var first = message();
        var failing = message();
        var after = message();
        given(repository.tryLockRelay()).willReturn(true);
        given(repository.findUnsentCreatedBefore(any(), eq(100))).willReturn(List.of(first, failing, after));
        var error = "eventType=child-account.changed eventId=" + failing.getEventId()
            + " key=1 reason=TimeoutException";
        // 다른 메시지를 보낼 때 이 스텁과 인자가 달라도 정상이다(엄격 스텁이 인자 불일치로 보지 않게)
        lenient().doThrow(new AuthOutboxSendException(error)).when(sender).send(failing);

        assertThatCode(() -> relayService().relay()).doesNotThrowAnyException();

        verify(sender, never()).send(after);
        assertThat(first.getSentAt()).isNotNull();
        assertThat(failing.getSentAt()).isNull();
        assertThat(failing.getAttemptCount()).isEqualTo(1);
        assertThat(failing.getLastError()).isEqualTo(error);
        assertThat(after.getAttemptCount()).isZero();
        assertThat(output.getAll()).contains("WARN").contains(error)
            .doesNotContain(AuthOutboxMessageFixture.PAYLOAD);
    }

    private AuthOutboxRelayService relayService() {
        return new AuthOutboxRelayService(repository, sender);
    }

    private static AuthOutboxMessage message() {
        return AuthOutboxMessageFixture.childAccountChanged(UUID.randomUUID());
    }
}
