package com.bbcc.kidly.boundedcontext.order.app.deadletter.dto;

/**
 * DLT로 온 메시지 하나(#601). 받는 쪽(in)이 DLT 헤더 · 원래 헤더에서 읽어 넘긴다. 읽지 못한 값은 null.
 *
 * @param sourceTopic 원래 토픽(DLT 헤더). 헤더가 없으면 DLT 자신의 토픽
 * @param exceptionClass 원인 예외 종류(없으면 바깥 예외 종류)
 * @param stackTrace DLT 헤더의 스택 전체(저장할 때 앞부분만 남긴다)
 */
public record ReceiveDeadLetterCommand(
    String sourceTopic,
    int sourcePartition,
    long sourceOffset,
    String messageKey,
    String eventType,
    String eventId,
    String payload,
    String exceptionClass,
    String exceptionMessage,
    String stackTrace) {
}
