package com.bbcc.kidly.boundedcontext.review.in.web.admin.dto;

import com.bbcc.kidly.boundedcontext.review.app.port.CommunityDeadLetters.Summary;
import java.time.LocalDateTime;

/**
 * 관리자 확인용 메타데이터. 원본 본문 · 예외 메시지 · 스택은 응답하지 않는다.
 */
public record CommunityDeadLetterResponse(
    String deadLetterId, String status, String sourceTopic, Integer sourcePartition, Long sourceOffset,
    String dltTopic, int dltPartition, long dltOffset, String eventType, String eventId, String messageKey,
    String failureType, int replayCount, String lastReplayedBy, LocalDateTime lastReplayedAt,
    String lastReplayFailureType, LocalDateTime createdAt) {

    public static CommunityDeadLetterResponse from(Summary row) {
        return new CommunityDeadLetterResponse(row.getId().toString(), row.getState().name(), row.getSourceTopic(),
            row.getSourcePartition(), row.getSourceOffset(), row.getDltTopic(), row.getDltPartition(),
            row.getDltOffset(),
            row.getEventType(), row.getEventId(), row.getMessageKey(), row.getFailureType(), row.getReplayCount(),
            row.getLastReplayedBy() == null ? null : row.getLastReplayedBy().toString(), row.getLastReplayedAt(),
            row.getLastReplayFailureType(), row.getCreatedAt());
    }
}
