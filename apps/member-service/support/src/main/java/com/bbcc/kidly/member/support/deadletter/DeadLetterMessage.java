package com.bbcc.kidly.member.support.deadletter;

/**
 * DLT에서 받은 메시지. 헤더에서 온 문자열은 컬럼 길이에 맞춰 자르고 NUL · 줄바꿈을 바꾼다.
 * 길이를 넘거나 NUL이 있으면 저장이 끝없이 실패하고, 줄바꿈은 로그를 위조한다.
 */
public record DeadLetterMessage(String dltTopic, int dltPartition, long dltOffset, String sourceTopic,
    Integer sourcePartition, Long sourceOffset, String messageKey, String eventType, String eventId, String payload,
    String failureType) {

    static final int SOURCE_TOPIC_LENGTH = 100;
    static final int HEADER_LENGTH = 100;
    static final int FAILURE_TYPE_LENGTH = 255;

    public DeadLetterMessage {
        sourceTopic = clip(sourceTopic, SOURCE_TOPIC_LENGTH);
        messageKey = clip(messageKey, HEADER_LENGTH);
        eventType = clip(eventType, HEADER_LENGTH);
        eventId = clip(eventId, HEADER_LENGTH);
        failureType = clip(failureType, FAILURE_TYPE_LENGTH);
    }

    public static DeadLetterMessage from(DeadLetter deadLetter) {
        return new DeadLetterMessage(deadLetter.getDltTopic(), deadLetter.getDltPartition(),
            deadLetter.getDltOffset(), deadLetter.getSourceTopic(), deadLetter.getSourcePartition(),
            deadLetter.getSourceOffset(), deadLetter.getMessageKey(), deadLetter.getEventType(),
            deadLetter.getEventId(), deadLetter.getPayload(), deadLetter.getFailureType());
    }

    private static String clip(String value, int max) {
        if (value == null) {
            return null;
        }
        var text = value.replace((char) 0, '�').replace('\r', ' ').replace('\n', ' ');
        return text.length() <= max ? text : text.substring(0, max);
    }
}
