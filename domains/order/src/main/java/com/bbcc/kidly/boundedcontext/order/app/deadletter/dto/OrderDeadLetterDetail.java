package com.bbcc.kidly.boundedcontext.order.app.deadletter.dto;

import com.bbcc.kidly.boundedcontext.order.domain.deadletter.OrderDeadLetter;
import java.time.LocalDateTime;

/**
 * 관리자 DLT 메시지 상세(#601): 저장한 칸 전체 + 조회 시점의 주문 상태 + 권장 조치.
 *
 * @param orderStatus 관련 주문의 지금 상태(저장하지 않음). 관련 주문이 없거나 주문이 없으면 null
 * @param recommendedAction 실패 유형별 권장 조치 한 줄. 처리 · 종료돼 조치할 것이 없으면 null
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
    String recommendedAction) {

    public static OrderDeadLetterDetail of(OrderDeadLetter letter, String orderStatus, String recommendedAction) {
        return new OrderDeadLetterDetail(letter.getId(), letter.getSourceTopic(), letter.getSourcePartition(),
            letter.getSourceOffset(), letter.getMessageKey(), letter.getEventType(), letter.getEventId(),
            letter.getPayload(), letter.getProducer(), letter.getOccurredAt(), letter.getAggregateId(),
            letter.getCorrelationId(), letter.getOrderId(), orderStatus, letter.getFailureType().name(),
            letter.getExceptionClass(), letter.getExceptionMessage(), letter.getStackTraceHead(),
            letter.getFirstReceivedAt(), letter.getLastReceivedAt(), letter.getReceivedCount(),
            letter.getStatus().name(), letter.getHandledBy(), letter.getHandledAt(), letter.getMemo(),
            letter.getReprocessCount(), letter.getLastReprocessedAt(), letter.getLastReprocessResult(),
            recommendedAction);
    }
}
