package com.bbcc.kidly.member.support.deadletter;

import java.time.LocalDateTime;
import java.util.Optional;

public record DeadLetterResponse(
    String deadLetterId, String status, String sourceTopic, Integer sourcePartition, Long sourceOffset,
    String dltTopic, int dltPartition, long dltOffset, String eventType, String eventId, String messageKey,
    String failureType, int replayCount, String lastReplayedBy, LocalDateTime lastReplayedAt,
    String lastReplayFailureType, LocalDateTime createdAt) {

    public static DeadLetterResponse from(DeadLetterResult result) {
        var lastReplayedBy = Optional.ofNullable(result.lastReplayedBy()).map(String::valueOf).orElse(null);
        return new DeadLetterResponse(result.id().toString(), result.status(), result.sourceTopic(),
            result.sourcePartition(), result.sourceOffset(), result.dltTopic(), result.dltPartition(),
            result.dltOffset(), result.eventType(), result.eventId(), result.messageKey(), result.failureType(),
            result.replayCount(), lastReplayedBy, result.lastReplayedAt(), result.lastReplayFailureType(),
            result.createdAt());
    }
}
