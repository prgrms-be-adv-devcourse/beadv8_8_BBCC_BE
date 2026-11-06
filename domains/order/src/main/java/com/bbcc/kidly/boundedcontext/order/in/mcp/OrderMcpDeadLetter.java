package com.bbcc.kidly.boundedcontext.order.in.mcp;

import static com.bbcc.kidly.boundedcontext.order.app.OrderTextGuard.exceptionClassOnly;
import static com.bbcc.kidly.boundedcontext.order.app.OrderTextGuard.orderRefOrNull;
import static com.bbcc.kidly.boundedcontext.order.app.OrderTextGuard.receivedEventTypeOrNull;
import static com.bbcc.kidly.boundedcontext.order.app.OrderTextGuard.receivedProducerOrNull;
import static com.bbcc.kidly.boundedcontext.order.app.OrderTextGuard.uuidOrNull;

import com.bbcc.kidly.boundedcontext.order.app.deadletter.DeadLetterHandling;
import com.bbcc.kidly.boundedcontext.order.app.deadletter.dto.OrderDeadLetterDetail;
import java.time.LocalDateTime;

/**
 * MCP DLT 상세. 관리자 상세 API(OrderDeadLetterResponse)에서 원본 본문(payload, 개인정보가 들 수 있음) · 관리자 메모(자유 입력) ·
 * 예외 메시지 · 스택 앞부분(Kafka DLT 헤더라 받은 메시지 값이 실릴 수 있음)을 뺀 메타데이터만 준다. 실패는 예외 종류만. ID는 문자열.
 * 받은 메시지에서 온 칸은 OrderTextGuard로 거른다: 키 · aggregateId · correlationId는 관련 주문 ID와 같거나 UUID일 때만,
 * eventId는 UUID만, producer는 원래 토픽 kidly.{x}.events의 x와 같을 때만, eventType은 앞 마디가 그 x일 때만, 예외 종류는
 * 클래스 이름만, 재처리 결과는 '자체 보관으로 넘김'이면 HANDED_OFF · 그 밖은 예외 클래스 이름만. 형식이 아니면 null이다.
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

    /** 재처리 결과 '자체 보관으로 넘김'(재처리 대기 목록에서 처리) */
    static final String HANDED_OFF = "HANDED_OFF";

    static OrderMcpDeadLetter from(OrderDeadLetterDetail detail) {
        return new OrderMcpDeadLetter(String.valueOf(detail.deadLetterId()), detail.sourceTopic(),
            detail.sourcePartition(), detail.sourceOffset(), orderRefOrNull(detail.messageKey(), detail.orderId()),
            receivedEventTypeOrNull(detail.eventType(), detail.sourceTopic()), uuidOrNull(detail.eventId()),
            receivedProducerOrNull(detail.producer(), detail.sourceTopic()), detail.occurredAt(),
            orderRefOrNull(detail.aggregateId(), detail.orderId()),
            orderRefOrNull(detail.correlationId(), detail.orderId()), toText(detail.orderId()), detail.orderStatus(),
            detail.failureType(), exceptionClassOnly(detail.exceptionClass()), detail.firstReceivedAt(),
            detail.lastReceivedAt(), detail.receivedCount(), detail.status(), toText(detail.handledBy()),
            detail.handledAt(), detail.reprocessCount(), detail.lastReprocessedAt(),
            reprocessResultOf(detail.lastReprocessResult()), detail.recommendedAction(), detail.paymentEvent());
    }

    // '자체 보관으로 넘김'(한글 문구)은 코드 HANDED_OFF로, 그 밖(재처리 실패 예외 종류)은 예외 클래스 이름만
    private static String reprocessResultOf(String result) {
        return DeadLetterHandling.isHandedOff(result) ? HANDED_OFF : exceptionClassOnly(result);
    }

    private static String toText(Long id) {
        return id == null ? null : String.valueOf(id);
    }
}
