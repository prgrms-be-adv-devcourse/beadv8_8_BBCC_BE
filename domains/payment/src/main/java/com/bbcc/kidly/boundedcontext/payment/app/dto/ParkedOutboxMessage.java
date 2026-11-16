package com.bbcc.kidly.boundedcontext.payment.app.dto;

import com.bbcc.kidly.boundedcontext.payment.domain.PaymentOutboxMessage;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 격리된 결제 아웃박스 메시지. 본문은 개인 정보가 있을 수 있어 담지 않는다.
 *
 * @param messageKey Kafka 메시지 키(주문 · 정산서 ID 등). 같은 키의 뒤 메시지가 함께 멈춰 있다
 * @param attemptCount 격리될 때까지 보내려 한 횟수
 */
public record ParkedOutboxMessage(Long outboxId, UUID eventId, String eventType, String messageKey,
                                  int attemptCount, String lastError, LocalDateTime createdAt,
                                  LocalDateTime parkedAt) {

    public static ParkedOutboxMessage from(PaymentOutboxMessage message) {
        return new ParkedOutboxMessage(message.getId(), message.getEventId(), message.getEventType(),
            message.getMessageKey(), message.getAttemptCount(), message.getLastError(), message.getCreatedAt(),
            message.getParkedAt());
    }
}
