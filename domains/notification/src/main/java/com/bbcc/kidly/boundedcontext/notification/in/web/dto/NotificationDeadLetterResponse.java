package com.bbcc.kidly.boundedcontext.notification.in.web.dto;

import com.bbcc.kidly.boundedcontext.notification.app.port.NotificationDeadLetters.Summary;
import java.time.LocalDateTime;

/**
 * 관리자 확인용 메타데이터. 원본 본문 · 예외 메시지 · 스택은 응답하지 않는다.
 */
public record NotificationDeadLetterResponse(
    String deadLetterId, String status, String sourceTopic, Integer sourcePartition, Long sourceOffset,
    String dltTopic, int dltPartition, long dltOffset, String eventType, String eventId, String messageKey,
    String failureType, int replayCount, String lastReplayedBy, LocalDateTime lastReplayedAt,
    String lastReplayFailureType, LocalDateTime createdAt) {

    public static NotificationDeadLetterResponse from(Summary row) {
        return new NotificationDeadLetterResponse(row.getId().toString(), row.getState().name(), row.getSourceTopic(),
            row.getSourcePartition(), row.getSourceOffset(), row.getDltTopic(), row.getDltPartition(),
            row.getDltOffset(), row.getEventType(), row.getEventId(), row.getMessageKey(), row.getFailureType(),
            row.getReplayCount(), row.getLastReplayedBy() == null ? null : row.getLastReplayedBy().toString(),
            row.getLastReplayedAt(), row.getLastReplayFailureType(), row.getCreatedAt());
    }
}
