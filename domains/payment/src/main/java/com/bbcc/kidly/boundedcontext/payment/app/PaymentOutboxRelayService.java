package com.bbcc.kidly.boundedcontext.payment.app;

import com.bbcc.kidly.boundedcontext.payment.app.dto.PaymentOutboxRelayResult;
import com.bbcc.kidly.boundedcontext.payment.app.dto.PaymentOutboxRelayResult.Outcome;
import com.bbcc.kidly.boundedcontext.payment.app.port.PaymentEventSendException;
import com.bbcc.kidly.boundedcontext.payment.app.port.PaymentEventSender;
import com.bbcc.kidly.boundedcontext.payment.domain.PaymentOutboxMessage;
import com.bbcc.kidly.boundedcontext.payment.domain.PaymentOutboxRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결제 이벤트 아웃박스를 Kafka로 보낸다(#550). 업무는 이 결과와 상관없이 이미 커밋돼 있다.
 * 보내지 못하면 WARN만 남긴다. 예외 메시지에 eventType · eventId · 키 · 원인이 있고, 본문은 개인 정보가 있을 수 있어 남기지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentOutboxRelayService {

    private final PaymentOutboxRepository outboxRepository;
    private final PaymentEventSender sender;
    private final PaymentOutboxProperties properties;

    /**
     * 한 트랜잭션에서 릴레이 잠금을 얻고 안 보낸 메시지를 id 순서로 batch-size건 보낸다. 잠금을 못 얻으면 아무것도 하지 않는다.
     * 보낸 시각 · 실패 기록 · 잠금 해제가 커밋 한 번에 함께 된다. 보내다 실패하면 그 메시지에 실패를 기록하고 멈춰
     * 다음 실행이 같은 메시지부터 다시 보낸다(순서 유지). 보낸 뒤 커밋 전에 멈추면 다음 실행이 다시 보낸다(받는 쪽은 eventId로 거른다).
     */
    @Transactional
    public PaymentOutboxRelayResult relay() {
        if (!outboxRepository.tryLockRelay()) {
            return new PaymentOutboxRelayResult(Outcome.LOCKED, 0, 0);
        }
        List<PaymentOutboxMessage> unsent = outboxRepository.findUnsent(properties.batchSize());
        int sent = 0;
        for (PaymentOutboxMessage message : unsent) {
            try {
                sender.send(message);
            } catch (PaymentEventSendException e) {
                message.recordFailure(e.getMessage());
                // 같은 장애가 주기마다 반복되므로 스택 트레이스는 남기지 않는다
                log.warn("결제 아웃박스 전송 실패로 이번 릴레이를 멈춥니다. attempt={} {}",
                    message.getAttemptCount(), e.getMessage());
                return new PaymentOutboxRelayResult(Outcome.STOPPED, unsent.size(), sent);
            }
            message.markSent(LocalDateTime.now());
            sent++;
        }
        return new PaymentOutboxRelayResult(Outcome.COMPLETED, unsent.size(), sent);
    }
}
