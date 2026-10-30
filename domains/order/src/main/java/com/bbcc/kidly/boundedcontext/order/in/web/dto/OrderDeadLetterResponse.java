package com.bbcc.kidly.boundedcontext.order.in.web.dto;

import com.bbcc.kidly.boundedcontext.order.app.deadletter.dto.OrderDeadLetterDetail;
import java.time.LocalDateTime;

/**
 * 관리자 DLT 메시지 상세(#601). 오래 지나도 이것만 보고 판단할 수 있게 저장한 칸 전체를 준다. ID는 문자열, 값이 없으면 null.
 *
 * @param sourceTopic 원래 토픽(메시지가 처음 들어온 곳). 원래 파티션 · 오프셋과 함께 이 메시지를 가리킨다
 * @param payload 원본 본문 그대로
 * @param producer 본문 공통 칸에서 꺼낸 보낸 도메인(occurredAt · aggregateId · correlationId도 같음). 본문을 읽지 못하면 null
 * @param orderId 관련 주문 ID(결제 · 재고 이벤트). 없으면 null
 * @param orderStatus 관련 주문의 지금 상태(조회 시점 값, 저장하지 않음). 주문이 없으면 null
 * @param failureType READ_FAILED(읽기 실패) · STORE_FAILED(보관 실패) · PROCESS_FAILED(그 밖의 처리 실패)
 * @param stackTraceHead 예외 스택 앞부분(10줄)
 * @param receivedCount DLT로 온 횟수
 * @param status OPEN(확인 대상) · REPROCESSED(재처리됨) · CLOSED(종료됨)
 * @param handledBy 재처리 · 종료한 관리자 회원 ID
 * @param lastReprocessResult 마지막 재처리 실패 이유(성공했거나 시도 전이면 null)
 * @param recommendedAction 실패 유형별 권장 조치 한 줄. 처리 · 종료돼 조치할 것이 없으면 null
 */
public record OrderDeadLetterResponse(
    String deadLetterId,
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
    String orderId,
    String orderStatus,
    String failureType,
    String exceptionClass,
    String exceptionMessage,
    String stackTraceHead,
    LocalDateTime firstReceivedAt,
    LocalDateTime lastReceivedAt,
    int receivedCount,
    String status,
    String handledBy,
    LocalDateTime handledAt,
    String memo,
    int reprocessCount,
    LocalDateTime lastReprocessedAt,
    String lastReprocessResult,
    String recommendedAction) {

    public static OrderDeadLetterResponse from(OrderDeadLetterDetail detail) {
        return new OrderDeadLetterResponse(String.valueOf(detail.deadLetterId()), detail.sourceTopic(),
            detail.sourcePartition(), detail.sourceOffset(), detail.messageKey(), detail.eventType(),
            detail.eventId(), detail.payload(), detail.producer(), detail.occurredAt(), detail.aggregateId(),
            detail.correlationId(), toText(detail.orderId()), detail.orderStatus(), detail.failureType(),
            detail.exceptionClass(), detail.exceptionMessage(), detail.stackTraceHead(), detail.firstReceivedAt(),
            detail.lastReceivedAt(), detail.receivedCount(), detail.status(), toText(detail.handledBy()),
            detail.handledAt(), detail.memo(), detail.reprocessCount(), detail.lastReprocessedAt(),
            detail.lastReprocessResult(), detail.recommendedAction());
    }

    private static String toText(Long id) {
        return id == null ? null : String.valueOf(id);
    }
}
