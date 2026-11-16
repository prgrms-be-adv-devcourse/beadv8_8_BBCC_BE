package com.bbcc.kidly.boundedcontext.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

import com.bbcc.kidly.boundedcontext.payment.app.PaymentOutboxProperties;
import com.bbcc.kidly.boundedcontext.payment.app.PaymentMetrics;
import com.bbcc.kidly.boundedcontext.payment.app.PaymentOutboxRelayService;
import com.bbcc.kidly.boundedcontext.payment.app.dto.PaymentOutboxRelayResult;
import com.bbcc.kidly.boundedcontext.payment.app.port.PaymentEventSendException;
import com.bbcc.kidly.boundedcontext.payment.app.port.PaymentEventSender;
import com.bbcc.kidly.boundedcontext.payment.domain.PaymentOutboxMessage;
import com.bbcc.kidly.boundedcontext.payment.domain.PaymentOutboxRepository;
import java.time.Duration;
import java.util.UUID;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class PaymentOutboxRelayServiceTest {

    private static final int BATCH_SIZE = 100;
    private static final int MAX_ATTEMPTS = 3;

    @Mock
    private PaymentOutboxRepository outboxRepository;

    @Mock
    private PaymentEventSender sender;

    @Mock
    private PaymentMetrics metrics;

    private PaymentOutboxRelayService relayService;

    @BeforeEach
    void setUp() {
        relayService = new PaymentOutboxRelayService(outboxRepository, sender,
            new PaymentOutboxProperties(Duration.ofSeconds(1), BATCH_SIZE, Duration.ofDays(7), MAX_ATTEMPTS), metrics);
    }

    @Test
    @DisplayName("잠금을 얻으면 안 보낸 메시지를 받은 순서대로 보내고 보낸 시각을 남긴다")
    void sendInOrder() {
        PaymentOutboxMessage first = PaymentFixture.outboxMessage();
        PaymentOutboxMessage second = PaymentFixture.outboxMessage();
        given(outboxRepository.tryLockRelay()).willReturn(true);
        given(outboxRepository.findUnsent(BATCH_SIZE)).willReturn(List.of(first, second));

        PaymentOutboxRelayResult result = relayService.relay();

        InOrder inOrder = Mockito.inOrder(sender);
        inOrder.verify(sender).send(first);
        inOrder.verify(sender).send(second);
        assertThat(first.getSentAt()).isNotNull();
        assertThat(second.getSentAt()).isNotNull();
        assertThat(result).isEqualTo(new PaymentOutboxRelayResult(PaymentOutboxRelayResult.Outcome.COMPLETED, 2, 2, 0));
    }

    @Test
    @DisplayName("다른 릴레이가 잠금을 쥐고 있으면 읽지도 보내지도 않는다")
    void skipWhenLocked() {
        given(outboxRepository.tryLockRelay()).willReturn(false);

        PaymentOutboxRelayResult result = relayService.relay();

        then(outboxRepository).should(never()).findUnsent(BATCH_SIZE);
        then(sender).should(never()).send(any());
        assertThat(result).isEqualTo(new PaymentOutboxRelayResult(PaymentOutboxRelayResult.Outcome.LOCKED, 0, 0, 0));
    }

    @Test
    @DisplayName("보내다 실패하면 실패를 기록하고 멈춘다. 뒤 메시지는 보내지 않는다")
    void stopAtFailure(CapturedOutput output) {
        PaymentOutboxMessage first = PaymentFixture.outboxMessage();
        PaymentOutboxMessage failed = PaymentFixture.outboxMessage();
        PaymentOutboxMessage after = PaymentFixture.outboxMessage();
        given(outboxRepository.tryLockRelay()).willReturn(true);
        given(outboxRepository.findUnsent(BATCH_SIZE)).willReturn(List.of(first, failed, after));
        willAnswer(invocation -> {
            if (invocation.getArgument(0) == failed) {
                throw new PaymentEventSendException("결제 이벤트를 Kafka로 보냈는지 확인하지 못했습니다. "
                    + "eventType=payment.completed", new IllegalStateException("브로커 없음"));
            }
            return null;
        }).given(sender).send(any());

        PaymentOutboxRelayResult result = relayService.relay();

        then(sender).should(never()).send(after);
        assertThat(first.getSentAt()).isNotNull();
        assertThat(failed.getSentAt()).isNull();
        assertThat(failed.getAttemptCount()).isOne();
        assertThat(failed.getLastError()).contains("eventType=payment.completed");
        assertThat(after.getAttemptCount()).isZero();
        then(metrics).should().countOutboxSendFailure(failed.getEventType());
        assertThat(result).isEqualTo(new PaymentOutboxRelayResult(PaymentOutboxRelayResult.Outcome.STOPPED, 3, 1, 0));
        assertThat(output.getOut()).contains("WARN").contains("attempt=1").doesNotContain(failed.getPayload());
    }

    @Test
    @DisplayName("실패한 메시지는 보낸 시각이 비어 있어 다음 실행에서 같은 메시지부터 다시 보낸다")
    void retrySameMessageNextRun() {
        PaymentOutboxMessage failed = PaymentFixture.outboxMessage();
        PaymentOutboxMessage after = PaymentFixture.outboxMessage();
        given(outboxRepository.tryLockRelay()).willReturn(true);
        given(outboxRepository.findUnsent(BATCH_SIZE)).willReturn(List.of(failed, after));
        willThrow(new PaymentEventSendException("실패", null)).willDoNothing().given(sender).send(failed);
        relayService.relay();

        PaymentOutboxRelayResult result = relayService.relay();

        InOrder inOrder = Mockito.inOrder(sender);
        inOrder.verify(sender, Mockito.times(2)).send(failed);
        inOrder.verify(sender).send(after);
        assertThat(failed.getAttemptCount()).isEqualTo(2);
        assertThat(failed.getSentAt()).isNotNull();
        assertThat(result.outcome()).isEqualTo(PaymentOutboxRelayResult.Outcome.COMPLETED);
    }

    @Test
    @DisplayName("시도 상한에 닿은 메시지는 같은 키의 뒤 메시지만 미루고, 다른 메시지가 보내지면 격리한다")
    void parkExhaustedWhenOthersSend(CapturedOutput output) {
        PaymentOutboxMessage poison = exhausted("order-1");
        PaymentOutboxMessage sameKey = message("order-1");
        PaymentOutboxMessage otherKey = message("order-2");
        given(outboxRepository.tryLockRelay()).willReturn(true);
        given(outboxRepository.findUnsent(BATCH_SIZE)).willReturn(List.of(poison, sameKey, otherKey));
        willThrow(new PaymentEventSendException("RecordTooLargeException", null)).given(sender).send(poison);

        PaymentOutboxRelayResult result = relayService.relay();

        then(sender).should(never()).send(sameKey);
        assertThat(otherKey.getSentAt()).isNotNull();
        assertThat(poison.getParkedAt()).isNotNull();
        assertThat(poison.getSentAt()).isNull();
        assertThat(sameKey.getParkedAt()).isNull();
        assertThat(sameKey.getAttemptCount()).isZero();
        assertThat(result).isEqualTo(new PaymentOutboxRelayResult(PaymentOutboxRelayResult.Outcome.COMPLETED, 3, 1, 1));
        assertThat(output.getOut()).contains("ERROR").contains("key=order-1").doesNotContain(poison.getPayload());
    }

    @Test
    @DisplayName("시도 상한에 닿았어도 다른 메시지도 보내지 못하면(브로커 장애) 격리하지 않고 멈춘다")
    void keepExhaustedWhenBrokerDown() {
        PaymentOutboxMessage exhausted = exhausted("order-1");
        PaymentOutboxMessage otherKey = message("order-2");
        PaymentOutboxMessage last = message("order-3");
        given(outboxRepository.tryLockRelay()).willReturn(true);
        given(outboxRepository.findUnsent(BATCH_SIZE)).willReturn(List.of(exhausted, otherKey, last));
        willThrow(new PaymentEventSendException("브로커 없음", null)).given(sender).send(any());

        PaymentOutboxRelayResult result = relayService.relay();

        then(sender).should(never()).send(last);
        assertThat(exhausted.getParkedAt()).isNull();
        assertThat(otherKey.getAttemptCount()).isOne();
        assertThat(result).isEqualTo(new PaymentOutboxRelayResult(PaymentOutboxRelayResult.Outcome.STOPPED, 3, 0, 0));
    }

    @Test
    @DisplayName("시도 상한에 닿은 메시지가 보내지면 격리하지 않고 보낸 것으로 남긴다")
    void sendExhaustedWhenRecovered() {
        PaymentOutboxMessage exhausted = exhausted("order-1");
        given(outboxRepository.tryLockRelay()).willReturn(true);
        given(outboxRepository.findUnsent(BATCH_SIZE)).willReturn(List.of(exhausted));

        PaymentOutboxRelayResult result = relayService.relay();

        assertThat(exhausted.getSentAt()).isNotNull();
        assertThat(exhausted.getParkedAt()).isNull();
        assertThat(result).isEqualTo(new PaymentOutboxRelayResult(PaymentOutboxRelayResult.Outcome.COMPLETED, 1, 1, 0));
    }

    private static PaymentOutboxMessage message(String key) {
        return PaymentOutboxMessage.create(UUID.randomUUID(), "payment.completed", "kidly.payment.events", key,
            "{\"eventType\":\"payment.completed\"}");
    }

    // 다음 실패가 상한(MAX_ATTEMPTS)에 닿는 메시지
    private static PaymentOutboxMessage exhausted(String key) {
        PaymentOutboxMessage message = message(key);
        for (int i = 1; i < MAX_ATTEMPTS; i++) {
            message.recordFailure("실패");
        }
        return message;
    }
}
