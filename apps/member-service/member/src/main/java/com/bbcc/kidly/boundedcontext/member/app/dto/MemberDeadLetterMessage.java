package com.bbcc.kidly.boundedcontext.member.app.dto;

import com.bbcc.kidly.boundedcontext.member.domain.MemberDeadLetter;

/**
 * DLT에서 받은 메시지. 보관할 때와 원래 처리기로 재처리할 때 쓴다.
 */
public record MemberDeadLetterMessage(String dltTopic, int dltPartition, long dltOffset, String sourceTopic,
    Integer sourcePartition, Long sourceOffset, String messageKey, String eventType, String eventId, String payload,
    String failureType) {

    public static MemberDeadLetterMessage from(MemberDeadLetter deadLetter) {
        return new MemberDeadLetterMessage(deadLetter.getDltTopic(), deadLetter.getDltPartition(),
            deadLetter.getDltOffset(), deadLetter.getSourceTopic(), deadLetter.getSourcePartition(),
            deadLetter.getSourceOffset(), deadLetter.getMessageKey(), deadLetter.getEventType(),
            deadLetter.getEventId(), deadLetter.getPayload(), deadLetter.getFailureType());
    }

    public MemberDeadLetter toDeadLetter() {
        return MemberDeadLetter.received(dltTopic, dltPartition, dltOffset, sourceTopic, sourcePartition,
            sourceOffset, messageKey, eventType, eventId, payload, failureType);
    }
}
