package com.bbcc.kidly.boundedcontext.order.app.outbox;

import com.bbcc.kidly.boundedcontext.order.app.OrderLogContext;
import com.bbcc.kidly.boundedcontext.order.app.OrderOperationMetrics;
import com.bbcc.kidly.boundedcontext.order.app.config.OrderOutboxProperties;
import com.bbcc.kidly.boundedcontext.order.app.outbox.dto.OutboxRelayResult;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderEventSender;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderOutboxRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderOutboxRepository.OutboxMessage;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
    private final OrderOperationMetrics metrics;

    /**
     * 한 트랜잭션에서 안 보낸 메시지를 id 순서로 batch-size건 보낸다. 한 번에 한 서버에서만 도는 것은 스케줄러가 맡는다
     * (SingleServerJobRunner, #564).
     * 보냄 · 실패 · 격리 기록은 함께 커밋한다. 확실한 메시지 영구 실패만 격리해 같은 토픽 · 키를 보류하고 다른 키를 계속 보낸다.
     * 그 밖의 실패는 그 자리에서 멈춰 다음 실행이 다시 시도한다. 커밋 전 실패하면 같은 이벤트 ID로 다시 보낼 수 있다.
     */
    @Transactional
    public OutboxRelayResult relay() {
        List<OutboxMessage> unsent = outboxRepository.findUnsent(Limit.of(properties.batchSize()));
        int sent = 0;
        boolean quarantined = false;
        Set<StreamKey> blocked = new HashSet<>();
        for (OutboxMessage message : unsent) {
            StreamKey stream = new StreamKey(message.topic(), message.messageKey());
            if (blocked.contains(stream)) {
                continue;
            }
            // 보내는 동안 로그에 그 메시지의 eventId · eventType · correlationId(= 키)를 붙인다(#603)
            try (var ignored = OrderLogContext.open(message.eventId().toString(), message.eventType(),
                message.messageKey())) {
                try {
                    sender.send(message);
                } catch (RuntimeException e) {
                    String error = e.getClass().getSimpleName() + ": " + e.getMessage();
                    if (sender.isPermanentFailure(e)) {
                        OutboxMessage failed = outboxRepository.quarantine(message.id(), error, LocalDateTime.now());
                        metrics.countOutboxSendFailure(message.eventType(),
                            OrderOperationMetrics.SendFailure.QUARANTINED);
                        quarantined = true;
                        blocked.add(stream);
                        log.warn("아웃박스 영구 실패 격리: outboxId={} eventId={} eventType={} attempt={}",
                            failed.id(), failed.eventId(), failed.eventType(), failed.attemptCount());
                        continue;
                    }
                    OutboxMessage failed = outboxRepository.recordFailure(message.id(), error);
                    metrics.countOutboxSendFailure(message.eventType(), OrderOperationMetrics.SendFailure.RETRY);
                    log.warn("아웃박스 전송 실패: outboxId={} eventId={} eventType={} attempt={} error={}",
                        failed.id(), failed.eventId(), failed.eventType(), failed.attemptCount(), failed.lastError());
                    return new OutboxRelayResult(OutboxRelayResult.Outcome.STOPPED, unsent.size(), sent);
                }
            }
            outboxRepository.markSent(message.id(), LocalDateTime.now());
            sent++;
        }
        return new OutboxRelayResult(quarantined ? OutboxRelayResult.Outcome.QUARANTINED
            : OutboxRelayResult.Outcome.COMPLETED, unsent.size(), sent);
    }
    private record StreamKey(String topic, String key) {
    }
}
