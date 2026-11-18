package com.bbcc.kidly.member.support.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class OutboxRelayTest {

    @Mock
    private OutboxRepository<TestOutboxMessage> repository;

    @Mock
    private OutboxSender sender;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Test
    @DisplayName("잠금을 얻은 뒤 보내지 않은 메시지를 받은 순서대로 보내고, 보낸 메시지마다 보낸 시각과 시도 1회를 남긴다")
    void sendInOrderAfterLock() {
        var first = message();
        var second = message();
        given(repository.tryLockRelay()).willReturn(true);
        given(repository.findUnsentCreatedBefore(any(), eq(100))).willReturn(List.of(first, second));

        relay().relay();

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

        relay().relay();

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
        var error = "회원 이벤트를 Kafka로 보냈는지 확인하지 못했습니다. eventType=member.withdrawn eventId="
            + failing.getEventId() + " key=1 reason=broker down";
        // 다른 메시지를 보낼 때 이 스텁과 인자가 달라도 정상이다(엄격 스텁이 인자 불일치로 보지 않게)
        lenient().doThrow(new OutboxSendException(error, new IllegalStateException("broker down")))
            .when(sender).send(failing);

        assertThatCode(() -> relay().relay()).doesNotThrowAnyException();

        verify(sender, never()).send(after);
        assertThat(first.getSentAt()).isNotNull();
        assertThat(failing.getSentAt()).isNull();
        assertThat(failing.getAttemptCount()).isEqualTo(1);
        assertThat(failing.getLastError()).isEqualTo(error);
        assertThat(after.getAttemptCount()).isZero();
        assertThat(output.getAll()).contains("WARN").contains("회원 아웃박스 전송 실패로 이번 릴레이를 멈춥니다")
            .contains(error).doesNotContain(TestOutboxMessage.PAYLOAD);
    }

    @Test
    @DisplayName("바로 전송은 보내지 못한 메시지에서 멈추고, 그 앞까지 보낸 것만 한 번에 기록한다")
    void sendBatchStopsAtFailureAndRecordsOnlySent() {
        var first = mock(TestOutboxMessage.class);
        var failing = mock(TestOutboxMessage.class);
        var last = mock(TestOutboxMessage.class);
        when(first.getId()).thenReturn(1L);
        lenient().doThrow(new OutboxSendException("실패", new RuntimeException())).when(sender).send(failing);
        doAnswer(invocation -> {
            Consumer<TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        relay().sendBatch(List.of(first, failing, last));

        verify(sender, never()).send(last);
        verify(repository).markSent(eq(List.of(1L)), any());
    }

    @Test
    @DisplayName("보관 기간(7일)보다 먼저 보낸 메시지를 지운다. 기준 시각은 실행한 때로부터 7일 전이다")
    void deleteMessagesSentBeforeRetention() {
        var before = LocalDateTime.now();

        relay().cleanUp();

        var after = LocalDateTime.now();
        var cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(repository).deleteSentBefore(cutoff.capture());
        assertThat(cutoff.getValue()).isBetween(before.minusDays(7), after.minusDays(7));
    }

    private OutboxRelay<TestOutboxMessage> relay() {
        return new OutboxRelay<>("회원", "test-outbox-dispatcher", repository, sender, transactionTemplate) {
        };
    }

    private static TestOutboxMessage message() {
        return TestOutboxMessage.withdrawn(UUID.randomUUID());
    }
}
