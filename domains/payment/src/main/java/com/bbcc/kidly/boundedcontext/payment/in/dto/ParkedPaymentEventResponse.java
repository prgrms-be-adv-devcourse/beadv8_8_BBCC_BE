package com.bbcc.kidly.boundedcontext.payment.in.dto;

import com.bbcc.kidly.boundedcontext.payment.app.dto.ParkedOutboxMessage;
import java.time.LocalDateTime;
import java.util.UUID;

public record ParkedPaymentEventResponse(Long outboxId, UUID eventId, String eventType, String messageKey,
                                         int attemptCount, String lastError, LocalDateTime createdAt,
                                         LocalDateTime parkedAt) {

    public static ParkedPaymentEventResponse from(ParkedOutboxMessage message) {
        return new ParkedPaymentEventResponse(message.outboxId(), message.eventId(), message.eventType(),
            message.messageKey(), message.attemptCount(), message.lastError(), message.createdAt(),
            message.parkedAt());
    }
}
