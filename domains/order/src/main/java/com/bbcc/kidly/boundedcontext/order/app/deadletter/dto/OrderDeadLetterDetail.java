package com.bbcc.kidly.boundedcontext.order.app.deadletter.dto;

import com.bbcc.kidly.boundedcontext.order.app.deadletter.DeadLetterHandling;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderDeadLetterRepository.DeadLetter;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderDeadLetterRepository.Handling;
import java.time.LocalDateTime;

/**
 * 관리자 DLT 메시지 상세(#601): 저장한 칸 전체 + 조회 시점의 주문 상태 + 권장 조치.
 *
 * @param orderStatus 관련 주문의 지금 상태(저장하지 않음). 관련 주문이 없거나 주문이 없으면 null
 * @param recommendedAction 실패 유형별 권장 조치 한 줄. 처리 · 종료돼 조치할 것이 없으면 null
 * @param paymentEvent 결제 토픽에서 온 메시지(#632)
 */
public record OrderDeadLetterDetail(
    Long deadLetterId,
    String sourceTopic,
    int sourcePartition,
    long sourceOffset,
    String messageKey,
    String eventType,
    String eventId,
    String payload,
    String producer,
    LocalDateTime occurredAt,
    String aggregateId,
    String correlationId,
    Long orderId,
    String orderStatus,
    String failureType,
    String exceptionClass,
    String exceptionMessage,
    String stackTraceHead,
    LocalDateTime firstReceivedAt,
    LocalDateTime lastReceivedAt,
    int receivedCount,
    String status,
    Long handledBy,
    LocalDateTime handledAt,
    String memo,
    int reprocessCount,
    LocalDateTime lastReprocessedAt,
    String lastReprocessResult,
    String recommendedAction,
    boolean paymentEvent) {

    public static OrderDeadLetterDetail of(DeadLetter letter, String orderStatus, String recommendedAction) {
        Handling handling = letter.handling();
        return new OrderDeadLetterDetail(letter.id(), letter.sourceTopic(), letter.sourcePartition(),
            letter.sourceOffset(), letter.messageKey(), letter.eventType(), letter.eventId(),
            letter.payload(), letter.producer(), letter.occurredAt(), letter.aggregateId(),
            letter.correlationId(), letter.orderId(), orderStatus, letter.failureType().name(),
            letter.exceptionClass(), letter.exceptionMessage(), letter.stackTraceHead(),
            letter.firstReceivedAt(), letter.lastReceivedAt(), letter.receivedCount(),
            handling.status().name(), handling.handledBy(), handling.handledAt(), handling.memo(),
            handling.reprocessCount(), handling.lastReprocessedAt(), handling.lastReprocessResult(),
            recommendedAction, DeadLetterHandling.isPaymentEvent(letter));
    }
}
