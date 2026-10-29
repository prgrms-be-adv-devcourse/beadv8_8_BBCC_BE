package com.bbcc.kidly.boundedcontext.order.app.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bbcc.kidly.boundedcontext.order.app.outbox.dto.OutboxRelayResult;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderEventSender;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderOutboxRepository;
import com.bbcc.kidly.boundedcontext.order.domain.outbox.OrderOutboxMessage;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.domain.Limit;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class OrderOutboxRelayServiceTest {

    private static final String SECRET_PAYLOAD = "{\"shippingAddress\":\"비밀 주소\"}";
    private static final OrderOutboxProperties PROPERTIES =
        new OrderOutboxProperties(Duration.ofSeconds(1), 100, Duration.ofDays(7));

    @Mock
    private OrderOutboxRepository outboxRepository;

    @Mock
    private OrderEventSender sender;

    private OrderOutboxRelayService relayService;

    @BeforeEach
    void setUp() {
        relayService = new OrderOutboxRelayService(outboxRepository, sender, PROPERTIES);
    }

    @Nested
    @DisplayName("릴레이")
    class Relay {

        @Test
        @DisplayName("잠금을 얻지 못하면(다른 릴레이가 도는 중) 읽지도 보내지도 않는다")
        void skipsWhenLocked() {
            when(outboxRepository.tryLockRelay()).thenReturn(false);

            OutboxRelayResult result = relayService.relay();

            assertThat(result).isEqualTo(new OutboxRelayResult(OutboxRelayResult.Outcome.LOCKED, 0, 0));
            verify(outboxRepository, never()).findBySentAtIsNullOrderByIdAsc(any());
            verifyNoInteractions(sender);
        }

        @Test
        @DisplayName("안 보낸 메시지를 batch-size만큼 읽어 id 순서대로 보내고, 보낼 때마다 보낸 시각을 기록한다")
        void sendsInOrderAndMarksSent() {
            OrderOutboxMessage first = message(1L);
            OrderOutboxMessage second = message(2L);
            givenUnsent(first, second);

            OutboxRelayResult result = relayService.relay();

            assertThat(result).isEqualTo(new OutboxRelayResult(OutboxRelayResult.Outcome.COMPLETED, 2, 2));
            InOrder order = inOrder(sender);
            order.verify(sender).send(first);
            order.verify(sender).send(second);
            ArgumentCaptor<Limit> limit = ArgumentCaptor.forClass(Limit.class);
            verify(outboxRepository).findBySentAtIsNullOrderByIdAsc(limit.capture());
            assertThat(limit.getValue().max()).isEqualTo(100);
            assertThat(first.getSentAt()).isNotNull();
            assertThat(second.getSentAt()).isNotNull();
        }

        @Test
        @DisplayName("보내다 실패하면 그 메시지에 실패를 기록하고 그 자리에서 멈춘다(뒤 메시지는 보내지 않고 보내지 않은 채로 남는다)")
        void stopsAtFirstFailure() {
            OrderOutboxMessage first = message(1L);
            OrderOutboxMessage failing = message(2L);
            OrderOutboxMessage after = message(3L);
            givenUnsent(first, failing, after);
            // 다른 메시지를 보낼 때는 이 스텁과 인자가 달라도 정상(엄격 스텁이 예외로 보지 않게)
            lenient().doThrow(new IllegalStateException(
                "TimeoutException: Topic not present in metadata after 5000 ms.")).when(sender).send(failing);

            OutboxRelayResult result = relayService.relay();

            assertThat(result).isEqualTo(new OutboxRelayResult(OutboxRelayResult.Outcome.STOPPED, 3, 1));
            verify(sender, never()).send(after);
            assertThat(first.getSentAt()).isNotNull();
            assertThat(failing.getSentAt()).isNull();
            assertThat(failing.getAttemptCount()).isEqualTo(1);
            assertThat(failing.getLastError())
                .isEqualTo("IllegalStateException: TimeoutException: Topic not present in metadata after 5000 ms.");
            assertThat(after.getSentAt()).isNull();
            assertThat(after.getAttemptCount()).isZero();
        }

        @Test
        @DisplayName("다음 실행은 실패한 메시지부터 다시 보내고, 성공하면 시도는 실패를 포함해 2")
        void retriesFromFailedMessage() {
            OrderOutboxMessage failing = message(2L);
            givenUnsent(failing);
            doThrow(new IllegalStateException("broker down")).doNothing().when(sender).send(failing);
            relayService.relay();

            OutboxRelayResult result = relayService.relay();

            assertThat(result).isEqualTo(new OutboxRelayResult(OutboxRelayResult.Outcome.COMPLETED, 1, 1));
            assertThat(failing.getSentAt()).isNotNull();
            assertThat(failing.getAttemptCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("실패 WARN에는 메시지 ID · 이벤트 ID · 종류 · 시도 · 오류만 남기고 본문은 남기지 않는다")
        void warnsWithoutPayload(CapturedOutput output) {
            OrderOutboxMessage failing = message(7L);
            givenUnsent(failing);
            doThrow(new IllegalStateException("broker down")).when(sender).send(failing);

            relayService.relay();

            assertThat(output).contains("아웃박스 전송 실패: outboxId=7 eventId=" + failing.getEventId()
                + " eventType=order.created attempt=1 error=IllegalStateException: broker down");
            assertThat(output).doesNotContain("비밀 주소");
        }
    }

    @Nested
    @DisplayName("정리")
    class CleanUp {

        @Test
        @DisplayName("보관 기간(7일) 전보다 먼저 보낸 메시지를 지운다")
        void deletesSentBeforeRetention() {
            when(outboxRepository.deleteSentBefore(any())).thenReturn(3);
            LocalDateTime before = LocalDateTime.now().minusDays(7);

            int deleted = relayService.cleanUp();

            LocalDateTime after = LocalDateTime.now().minusDays(7);
            ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(outboxRepository).deleteSentBefore(cutoff.capture());
            assertThat(cutoff.getValue()).isBetween(before, after);
            assertThat(deleted).isEqualTo(3);
        }
    }

    private void givenUnsent(OrderOutboxMessage... messages) {
        when(outboxRepository.tryLockRelay()).thenReturn(true);
        when(outboxRepository.findBySentAtIsNullOrderByIdAsc(any())).thenAnswer(invocation ->
            List.of(messages).stream().filter(message -> message.getSentAt() == null).toList());
    }

    private static OrderOutboxMessage message(Long id) {
        OrderOutboxMessage message = OrderOutboxMessage.create(UUID.randomUUID(), "order.created",
            "kidly.order.events", "1024", SECRET_PAYLOAD);
        ReflectionTestUtils.setField(message, "id", id);
        return message;
    }
}
