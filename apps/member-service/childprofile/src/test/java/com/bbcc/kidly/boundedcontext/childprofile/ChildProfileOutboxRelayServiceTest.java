package com.bbcc.kidly.boundedcontext.childprofile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.bbcc.kidly.boundedcontext.childprofile.app.outbox.ChildProfileOutboxRelayService;
import com.bbcc.kidly.boundedcontext.childprofile.app.port.outbox.ChildProfileEventSendException;
import com.bbcc.kidly.boundedcontext.childprofile.app.port.outbox.ChildProfileEventSender;
import com.bbcc.kidly.boundedcontext.childprofile.domain.outbox.ChildProfileOutboxMessage;
import com.bbcc.kidly.boundedcontext.childprofile.domain.outbox.ChildProfileOutboxRepository;
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
class ChildProfileOutboxRelayServiceTest {

    @Mock
    private ChildProfileOutboxRepository outboxRepository;

    @Mock
    private ChildProfileEventSender sender;

    @Test
    @DisplayName("잠금을 얻은 뒤 보내지 않은 메시지를 받은 순서대로 보내고, 보낸 메시지마다 보낸 시각과 시도 1회를 남긴다")
    void sendInOrderAfterLock() {
        var first = message();
        var second = message();
        given(outboxRepository.tryLockRelay()).willReturn(true);
        given(outboxRepository.findUnsent(100)).willReturn(List.of(first, second));

        relayService().relay();

        var order = inOrder(outboxRepository, sender);
        order.verify(outboxRepository).tryLockRelay();
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
        given(outboxRepository.tryLockRelay()).willReturn(false);

        relayService().relay();

        verify(outboxRepository, never()).findUnsent(anyInt());
        verifyNoInteractions(sender);
    }

    @Test
    @DisplayName("보내다 실패하면 그 메시지에 시도 1회와 오류를 남기고 멈춘다. 로그에는 예외 메시지만 남기고 본문은 남기지 않는다")
    void recordFailureAndStop(CapturedOutput output) {
        var first = message();
        var failing = message();
        var after = message();
        given(outboxRepository.tryLockRelay()).willReturn(true);
        given(outboxRepository.findUnsent(100)).willReturn(List.of(first, failing, after));
        var error = "자녀 프로필 이벤트를 Kafka로 보냈는지 확인하지 못했습니다. eventType=child.changed eventId="
            + failing.getEventId() + " key=3 reason=broker down";
        // 다른 메시지를 보낼 때 이 스텁과 인자가 달라도 정상이다(엄격 스텁이 인자 불일치로 보지 않게)
        lenient().doThrow(new ChildProfileEventSendException(error, new IllegalStateException("broker down")))
            .when(sender).send(failing);

        assertThatCode(() -> relayService().relay()).doesNotThrowAnyException();

        verify(sender, never()).send(after);
        assertThat(first.getSentAt()).isNotNull();
        assertThat(failing.getSentAt()).isNull();
        assertThat(failing.getAttemptCount()).isEqualTo(1);
        assertThat(failing.getLastError()).isEqualTo(error);
        assertThat(after.getAttemptCount()).isZero();
        assertThat(output.getAll()).contains("WARN").contains(error)
            .doesNotContain(ChildProfileOutboxMessageFixture.PAYLOAD);
    }

    private ChildProfileOutboxRelayService relayService() {
        return new ChildProfileOutboxRelayService(outboxRepository, sender);
    }

    private static ChildProfileOutboxMessage message() {
        return ChildProfileOutboxMessage.create(ChildProfileOutboxMessageFixture.childChanged(UUID.randomUUID()));
    }
}
