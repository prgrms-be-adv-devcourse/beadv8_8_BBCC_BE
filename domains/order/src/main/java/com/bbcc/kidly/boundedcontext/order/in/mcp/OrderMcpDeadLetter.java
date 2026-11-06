package com.bbcc.kidly.boundedcontext.order.in.mcp;

import static com.bbcc.kidly.boundedcontext.order.app.OrderTextGuard.eventTypeOrNull;
import static com.bbcc.kidly.boundedcontext.order.app.OrderTextGuard.exceptionClassOnly;
import static com.bbcc.kidly.boundedcontext.order.app.OrderTextGuard.idOrNull;
import static com.bbcc.kidly.boundedcontext.order.app.OrderTextGuard.producerOrNull;

import com.bbcc.kidly.boundedcontext.order.app.deadletter.dto.OrderDeadLetterDetail;
import java.time.LocalDateTime;

/**
 * MCP DLT 상세. 관리자 상세 API(OrderDeadLetterResponse)에서 원본 본문(payload, 개인정보가 들 수 있음) · 관리자 메모(자유 입력) ·
 * 예외 메시지 · 스택 앞부분(Kafka DLT 헤더라 받은 메시지 값이 실릴 수 있음)을 뺀 메타데이터만 준다. 실패는 예외 종류만. ID는 문자열.
 * 받은 메시지에서 온 칸(키 · eventType · eventId · producer · aggregateId · correlationId · 예외 종류 · 재처리 결과)은
 * OrderTextGuard 형식(ID · 이벤트 타입 · 도메인 이름 · 예외 클래스 이름)일 때만 내보내고 아니면 null이다.
 */
public record OrderMcpDeadLetter(
    String deadLetterId,
    String sourceTopic,
    int sourcePartition,
    long sourceOffset,
    String messageKey,
    String eventType,
    String eventId,
    String producer,
    LocalDateTime occurredAt,
    String aggregateId,
    String correlationId,
    String orderId,
    String orderStatus,
    String failureType,
    String exceptionClass,
    LocalDateTime firstReceivedAt,
    LocalDateTime lastReceivedAt,
    int receivedCount,
    String status,
    String handledBy,
    LocalDateTime handledAt,
    int reprocessCount,
    LocalDateTime lastReprocessedAt,
    String lastReprocessResult,
    String recommendedAction,
    boolean paymentEvent) {

    static OrderMcpDeadLetter from(OrderDeadLetterDetail detail) {
        return new OrderMcpDeadLetter(String.valueOf(detail.deadLetterId()), detail.sourceTopic(),
            detail.sourcePartition(), detail.sourceOffset(), idOrNull(detail.messageKey()),
            eventTypeOrNull(detail.eventType()), idOrNull(detail.eventId()), producerOrNull(detail.producer()),
            detail.occurredAt(), idOrNull(detail.aggregateId()), idOrNull(detail.correlationId()),
            toText(detail.orderId()), detail.orderStatus(), detail.failureType(),
            exceptionClassOnly(detail.exceptionClass()), detail.firstReceivedAt(), detail.lastReceivedAt(),
            detail.receivedCount(), detail.status(), toText(detail.handledBy()), detail.handledAt(),
            detail.reprocessCount(), detail.lastReprocessedAt(), exceptionClassOnly(detail.lastReprocessResult()),
            detail.recommendedAction(), detail.paymentEvent());
    }

    private static String toText(Long id) {
        return id == null ? null : String.valueOf(id);
    }
}
