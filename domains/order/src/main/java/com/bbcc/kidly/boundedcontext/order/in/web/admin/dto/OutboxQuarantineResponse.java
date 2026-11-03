package com.bbcc.kidly.boundedcontext.order.in.web.admin.dto;

import com.bbcc.kidly.boundedcontext.order.app.port.OrderOutboxRepository.OutboxStatus;
import java.time.LocalDateTime;

/**
 * 관리자용 아웃박스 상태. 큰 본문과 개인정보는 응답하지 않는다.
 */
public record OutboxQuarantineResponse(
    String outboxId,
    String eventId,
    String eventType,
    String topic,
    String messageKey,
    String status,
    int attemptCount,
    String lastError,
    LocalDateTime createdAt,
    LocalDateTime quarantinedAt,
    LocalDateTime sentAt,
    LocalDateTime lastRetriedAt,
    String lastRetriedBy,
    int retryCount) {

    public static OutboxQuarantineResponse from(OutboxStatus row) {
        String status = row.getSentAt() != null ? "SENT" : row.getQuarantinedAt() != null ? "QUARANTINED" : "PENDING";
        return new OutboxQuarantineResponse(row.getId().toString(), row.getEventId().toString(), row.getEventType(),
            row.getTopic(), row.getMessageKey(), status, row.getAttemptCount(), row.getLastError(), row.getCreatedAt(),
            row.getQuarantinedAt(), row.getSentAt(), row.getLastRetriedAt(),
            row.getLastRetriedBy() == null ? null : row.getLastRetriedBy().toString(), row.getRetryCount());
    }
}
