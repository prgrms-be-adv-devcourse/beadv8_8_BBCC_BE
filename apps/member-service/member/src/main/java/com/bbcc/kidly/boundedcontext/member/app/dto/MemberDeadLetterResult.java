package com.bbcc.kidly.boundedcontext.member.app.dto;

import com.bbcc.kidly.boundedcontext.member.domain.MemberDeadLetter;
import java.time.LocalDateTime;

public record MemberDeadLetterResult(
    Long id, String status, String sourceTopic, Integer sourcePartition, Long sourceOffset, String dltTopic,
    int dltPartition, long dltOffset, String eventType, String eventId, String messageKey, String failureType,
    int replayCount, Long lastReplayedBy, LocalDateTime lastReplayedAt, String lastReplayFailureType,
    LocalDateTime createdAt) {

    public static MemberDeadLetterResult from(MemberDeadLetter deadLetter) {
        return new MemberDeadLetterResult(deadLetter.getId(), deadLetter.getStatus().name(),
            deadLetter.getSourceTopic(), deadLetter.getSourcePartition(), deadLetter.getSourceOffset(),
            deadLetter.getDltTopic(), deadLetter.getDltPartition(), deadLetter.getDltOffset(),
            deadLetter.getEventType(), deadLetter.getEventId(), deadLetter.getMessageKey(),
            deadLetter.getFailureType(), deadLetter.getReplayCount(), deadLetter.getLastReplayedBy(),
            deadLetter.getLastReplayedAt(), deadLetter.getLastReplayFailureType(), deadLetter.getCreatedAt());
    }
}
