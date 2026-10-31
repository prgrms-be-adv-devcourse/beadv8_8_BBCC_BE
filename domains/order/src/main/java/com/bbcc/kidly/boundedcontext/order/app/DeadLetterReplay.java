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

    /**
     * 다시 실행한 결과. 실패는 예외로 알린다.
     */
    public enum Outcome {
        /** 처리를 마쳤다(이미 처리된 이벤트면 아무것도 하지 않음) */
        PROCESSED,
        /** 처리가 실패해 주문 자체 보관(결제 반영 실패 · 이벤트 처리 실패 기록)에 넘겼다 */
        HANDED_OFF
    }
}
