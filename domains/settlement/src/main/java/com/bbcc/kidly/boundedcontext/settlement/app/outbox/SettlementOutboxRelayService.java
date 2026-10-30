package com.bbcc.kidly.boundedcontext.settlement.app.outbox;

import com.bbcc.kidly.boundedcontext.settlement.app.outbox.SettlementOutboxRelayResult.Outcome;
import com.bbcc.kidly.boundedcontext.settlement.app.port.SettlementEventSendException;
import com.bbcc.kidly.boundedcontext.settlement.app.port.SettlementEventSender;
import com.bbcc.kidly.boundedcontext.settlement.domain.outbox.SettlementOutboxMessage;
import com.bbcc.kidly.boundedcontext.settlement.domain.outbox.SettlementOutboxRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 정산 이벤트 아웃박스를 Kafka로 보낸다. 업무는 이 결과와 상관없이 이미 커밋돼 있다.
 * 보내지 못하면 WARN만 남긴다. 예외 메시지에 eventType · eventId · 키 · 원인이 있고, 본문은 개인 정보가 있을 수 있어 남기지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementOutboxRelayService {

    private final SettlementOutboxRepository outboxRepository;
    private final SettlementEventSender sender;
    private final SettlementOutboxProperties properties;

    /**
     * 한 트랜잭션에서 릴레이 잠금을 얻고 안 보낸 메시지를 id 순서로 batch-size건 보낸다. 잠금을 못 얻으면 아무것도 하지 않는다.
     * 보낸 시각 · 실패 기록 · 잠금 해제가 커밋 한 번에 함께 된다. 보내다 실패하면 그 메시지에 실패를 기록하고 멈춰
     * 다음 실행이 같은 메시지부터 다시 보낸다(순서 유지). 보낸 뒤 커밋 전에 멈추면 다음 실행이 다시 보낸다(받는 쪽은 eventId로 거른다).
     */
    @Transactional
    public SettlementOutboxRelayResult relay() {
        if (!outboxRepository.tryLockRelay()) {
            return new SettlementOutboxRelayResult(Outcome.LOCKED, 0, 0);
        }
        List<SettlementOutboxMessage> unsent = outboxRepository.findUnsent(properties.batchSize());
        int sent = 0;
        for (SettlementOutboxMessage message : unsent) {
            try {
                sender.send(message);
            } catch (SettlementEventSendException e) {
                message.recordFailure(e.getMessage());
                // 같은 장애가 주기마다 반복되므로 스택 트레이스는 남기지 않는다
                log.warn("정산 아웃박스 전송 실패로 이번 릴레이를 멈춥니다. attempt={} {}",
                    message.getAttemptCount(), e.getMessage());
                return new SettlementOutboxRelayResult(Outcome.STOPPED, unsent.size(), sent);
            }
            message.markSent(LocalDateTime.now());
            sent++;
        }
        return new SettlementOutboxRelayResult(Outcome.COMPLETED, unsent.size(), sent);
    }
}
