package com.bbcc.kidly.boundedcontext.auth.app.dto;

import com.bbcc.kidly.boundedcontext.auth.domain.AuthDeadLetter;

public record AuthDeadLetterMessage(String dltTopic, int dltPartition, long dltOffset, String sourceTopic,
    Integer sourcePartition, Long sourceOffset, String messageKey, String eventType, String eventId, String payload,
    String failureType) {

    public static AuthDeadLetterMessage from(AuthDeadLetter deadLetter) {
        return new AuthDeadLetterMessage(deadLetter.getDltTopic(), deadLetter.getDltPartition(),
            deadLetter.getDltOffset(), deadLetter.getSourceTopic(), deadLetter.getSourcePartition(),
            deadLetter.getSourceOffset(), deadLetter.getMessageKey(), deadLetter.getEventType(),
            deadLetter.getEventId(), deadLetter.getPayload(), deadLetter.getFailureType());
    }

    public AuthDeadLetter toDeadLetter() {
        return AuthDeadLetter.received(dltTopic, dltPartition, dltOffset, sourceTopic, sourcePartition,
            sourceOffset, messageKey, eventType, eventId, payload, failureType);
    }
}
