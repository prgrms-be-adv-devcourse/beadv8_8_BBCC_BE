package com.bbcc.kidly.boundedcontext.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

import com.bbcc.kidly.boundedcontext.settlement.app.outbox.SettlementOutboxProperties;
import com.bbcc.kidly.boundedcontext.settlement.app.outbox.SettlementOutboxRelayResult;
import com.bbcc.kidly.boundedcontext.settlement.app.outbox.SettlementOutboxRelayService;
import com.bbcc.kidly.boundedcontext.settlement.app.port.SettlementEventSendException;
import com.bbcc.kidly.boundedcontext.settlement.app.port.SettlementEventSender;
import com.bbcc.kidly.boundedcontext.settlement.domain.outbox.SettlementOutboxMessage;
import com.bbcc.kidly.boundedcontext.settlement.domain.outbox.SettlementOutboxRepository;
import java.time.Duration;
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
class SettlementOutboxRelayServiceTest {

    private static final int BATCH_SIZE = 100;

    @Mock
    private SettlementOutboxRepository outboxRepository;

    @Mock
    private SettlementEventSender sender;

    private SettlementOutboxRelayService relayService;

    @BeforeEach
    void setUp() {
        relayService = new SettlementOutboxRelayService(outboxRepository, sender,
            new SettlementOutboxProperties(Duration.ofSeconds(1), BATCH_SIZE, Duration.ofDays(7)));
    }

    @Test
    @DisplayName("잠금을 얻으면 안 보낸 메시지를 받은 순서대로 보내고 보낸 시각을 남긴다")
    void sendInOrder() {
        SettlementOutboxMessage first = SettlementFixture.outboxMessage();
        SettlementOutboxMessage second = SettlementFixture.outboxMessage();
        given(outboxRepository.tryLockRelay()).willReturn(true);
        given(outboxRepository.findUnsent(BATCH_SIZE)).willReturn(List.of(first, second));

        SettlementOutboxRelayResult result = relayService.relay();

        InOrder inOrder = Mockito.inOrder(sender);
        inOrder.verify(sender).send(first);
        inOrder.verify(sender).send(second);
        assertThat(first.getSentAt()).isNotNull();
        assertThat(second.getSentAt()).isNotNull();
        assertThat(result).isEqualTo(
            new SettlementOutboxRelayResult(SettlementOutboxRelayResult.Outcome.COMPLETED, 2, 2));
    }

    @Test
    @DisplayName("다른 릴레이가 잠금을 쥐고 있으면 읽지도 보내지도 않는다")
    void skipWhenLocked() {
        given(outboxRepository.tryLockRelay()).willReturn(false);

        SettlementOutboxRelayResult result = relayService.relay();

        then(outboxRepository).should(never()).findUnsent(BATCH_SIZE);
        then(sender).should(never()).send(any());
        assertThat(result).isEqualTo(
            new SettlementOutboxRelayResult(SettlementOutboxRelayResult.Outcome.LOCKED, 0, 0));
    }

    @Test
    @DisplayName("보내다 실패하면 실패를 기록하고 멈춘다. 뒤 메시지는 보내지 않는다")
    void stopAtFailure(CapturedOutput output) {
        SettlementOutboxMessage first = SettlementFixture.outboxMessage();
        SettlementOutboxMessage failed = SettlementFixture.outboxMessage();
        SettlementOutboxMessage after = SettlementFixture.outboxMessage();
        given(outboxRepository.tryLockRelay()).willReturn(true);
        given(outboxRepository.findUnsent(BATCH_SIZE)).willReturn(List.of(first, failed, after));
        willAnswer(invocation -> {
            if (invocation.getArgument(0) == failed) {
                throw new SettlementEventSendException("정산 이벤트를 Kafka로 보냈는지 확인하지 못했습니다. "
                    + "eventType=settlement.payout-requested", new IllegalStateException("브로커 없음"));
            }
            return null;
        }).given(sender).send(any());

        SettlementOutboxRelayResult result = relayService.relay();

        then(sender).should(never()).send(after);
        assertThat(first.getSentAt()).isNotNull();
        assertThat(failed.getSentAt()).isNull();
        assertThat(failed.getAttemptCount()).isOne();
        assertThat(failed.getLastError()).contains("eventType=settlement.payout-requested");
        assertThat(after.getAttemptCount()).isZero();
        assertThat(result).isEqualTo(
            new SettlementOutboxRelayResult(SettlementOutboxRelayResult.Outcome.STOPPED, 3, 1));
        assertThat(output.getOut()).contains("WARN").contains("attempt=1").doesNotContain(failed.getPayload());
    }

    @Test
    @DisplayName("실패한 메시지는 보낸 시각이 비어 있어 다음 실행에서 같은 메시지부터 다시 보낸다")
    void retrySameMessageNextRun() {
        SettlementOutboxMessage failed = SettlementFixture.outboxMessage();
        SettlementOutboxMessage after = SettlementFixture.outboxMessage();
        given(outboxRepository.tryLockRelay()).willReturn(true);
        given(outboxRepository.findUnsent(BATCH_SIZE)).willReturn(List.of(failed, after));
        willThrow(new SettlementEventSendException("실패", null)).willDoNothing().given(sender).send(failed);
        relayService.relay();

        SettlementOutboxRelayResult result = relayService.relay();

        InOrder inOrder = Mockito.inOrder(sender);
        inOrder.verify(sender, Mockito.times(2)).send(failed);
        inOrder.verify(sender).send(after);
        assertThat(failed.getAttemptCount()).isEqualTo(2);
        assertThat(failed.getSentAt()).isNotNull();
        assertThat(result.outcome()).isEqualTo(SettlementOutboxRelayResult.Outcome.COMPLETED);
    }
}
