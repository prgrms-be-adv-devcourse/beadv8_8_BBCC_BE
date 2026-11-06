package com.bbcc.kidly.boundedcontext.seller.in.dto;

import com.bbcc.kidly.boundedcontext.seller.app.dto.SellerDeadLetterResult;
import java.time.LocalDateTime;
import java.util.Optional;

public record SellerDeadLetterResponse(
    String deadLetterId, String status, String sourceTopic, Integer sourcePartition, Long sourceOffset,
    String dltTopic, int dltPartition, long dltOffset, String eventType, String eventId, String messageKey,
    String failureType, int replayCount, String lastReplayedBy, LocalDateTime lastReplayedAt,
    String lastReplayFailureType, LocalDateTime createdAt) {

    public static SellerDeadLetterResponse from(SellerDeadLetterResult result) {
        var lastReplayedBy = Optional.ofNullable(result.lastReplayedBy()).map(String::valueOf).orElse(null);
        return new SellerDeadLetterResponse(result.id().toString(), result.status(), result.sourceTopic(),
            result.sourcePartition(), result.sourceOffset(), result.dltTopic(), result.dltPartition(),
            result.dltOffset(), result.eventType(), result.eventId(), result.messageKey(), result.failureType(),
            result.replayCount(), lastReplayedBy, result.lastReplayedAt(), result.lastReplayFailureType(),
            result.createdAt());
    }
}
