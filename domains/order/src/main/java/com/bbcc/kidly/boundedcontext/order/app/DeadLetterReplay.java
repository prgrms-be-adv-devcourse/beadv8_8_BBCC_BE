package com.bbcc.kidly.boundedcontext.order.app;

/**
 * 저장한 DLT 메시지를 원래 처리로 다시 넘길 값(#601): 원래 위치 · 키 · 원래 헤더(eventType · eventId) · 본문.
 */
public record DeadLetterReplay(
    String sourceTopic,
    int sourcePartition,
    long sourceOffset,
    String messageKey,
    String eventType,
    String eventId,
    String payload) {
}
