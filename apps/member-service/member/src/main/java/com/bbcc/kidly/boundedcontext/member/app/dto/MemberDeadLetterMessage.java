package com.bbcc.kidly.boundedcontext.member.app.dto;

import com.bbcc.kidly.boundedcontext.member.domain.MemberDeadLetter;

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
