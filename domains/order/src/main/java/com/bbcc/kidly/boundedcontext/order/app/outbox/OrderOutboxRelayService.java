package com.bbcc.kidly.boundedcontext.order.app.outbox;

import com.bbcc.kidly.boundedcontext.order.app.OrderLogContext;
import com.bbcc.kidly.boundedcontext.order.app.outbox.dto.OutboxRelayResult;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderEventSender;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderOutboxRepository;
import com.bbcc.kidly.boundedcontext.order.domain.outbox.OrderOutboxMessage;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주문 이벤트 아웃박스를 Kafka로 보낸다(#540). 업무는 이 결과와 상관없이 이미 커밋돼 있다.
 * 로그: 보내지 못하면 WARN(메시지 ID · 이벤트 ID · 종류 · 시도 · 오류). 본문은 개인 정보가 있을 수 있어 남기지 않는다.
 * kidly.kafka.producer.enabled가 false면 만들지 않는다(Kafka 없이 도는 테스트). 값이 없으면 만든다.
 * 그때도 업무의 아웃박스 저장(OrderEventOutbox)과 정리(OrderOutboxCleanupService)는 그대로다.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "kidly.kafka.producer.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class OrderOutboxRelayService {

    private final OrderOutboxRepository outboxRepository;
    private final OrderEventSender sender;
    private final OrderOutboxProperties properties;

    /**
     * 한 트랜잭션에서 안 보낸 메시지를 id 순서로 batch-size건 보낸다. 한 번에 한 서버에서만 도는 것은 스케줄러가 맡는다
     * (SingleServerJobRunner, #564).
     * 보낸 시각 · 실패 기록이 커밋 한 번에 함께 된다. 보내다 실패하면 그 메시지에 실패를 기록하고 멈춰
     * 다음 실행이 같은 메시지부터 다시 보낸다(순서 유지). 보낸 뒤 커밋 전에 멈추면 다음 실행이 다시 보낸다(받는 쪽은 eventId로 거른다).
     */
    @Transactional
    public OutboxRelayResult relay() {
        List<OrderOutboxMessage> unsent = outboxRepository.findBySentAtIsNullOrderByIdAsc(
            Limit.of(properties.batchSize()));
        int sent = 0;
        for (OrderOutboxMessage message : unsent) {
            // 보내는 동안 로그에 그 메시지의 eventId · eventType · correlationId(= 키)를 붙인다(#603)
            try (var ignored = OrderLogContext.open(message.getEventId().toString(), message.getEventType(),
                message.getMessageKey())) {
                try {
                    sender.send(message);
                } catch (RuntimeException e) {
                    message.recordFailure(e.getClass().getSimpleName() + ": " + e.getMessage());
                    log.warn("아웃박스 전송 실패: outboxId={} eventId={} eventType={} attempt={} error={}",
                        message.getId(), message.getEventId(), message.getEventType(), message.getAttemptCount(),
                        message.getLastError());
                    return new OutboxRelayResult(OutboxRelayResult.Outcome.STOPPED, unsent.size(), sent);
                }
            }
            message.markSent(LocalDateTime.now());
            sent++;
        }
        return new OutboxRelayResult(OutboxRelayResult.Outcome.COMPLETED, unsent.size(), sent);
    }
}
