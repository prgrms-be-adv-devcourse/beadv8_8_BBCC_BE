package com.bbcc.kidly.boundedcontext.order.out.deadletter;

import com.bbcc.kidly.boundedcontext.order.app.port.OrderDeadLetterRepository.FailureType;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderDeadLetterRepository.Received;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

/**
 * DLT로 받은 메시지 한 번을 저장할 모양으로 맞춘 값(#601 · #642). 저장소가 이 값으로 처음 저장하거나 다시 받음을 센다.
 * 칸 길이를 넘는 값은 잘라 둔다: DLT 저장이 길이 때문에 실패하면 무한 재시도에 걸리므로 넣을 수 있는 만큼 남긴다.
 * 같은 이유로 PostgreSQL 문자열에 넣을 수 없는 NUL 문자(U+0000)는 모든 칸에서 대체 문자(U+FFFD)로 바꾼다(본문 포함).
 *
 * @param sourceTopic 원래 토픽(DLT 헤더). 원래 위치(토픽 · 파티션 · 오프셋)당 1건
 * @param payload 원본 본문 그대로(자르지 않음, NUL 문자만 바꿈)
 * @param producer 본문 공통 칸에서 꺼낸 보낸 도메인. 본문을 읽지 못하면 null(occurredAt · aggregateId · correlationId도 같음)
 * @param orderId 관련 주문 ID. 주문을 다루는 이벤트(결제 · 재고)에서 알 수 있을 때만
 * @param stackTraceHead 스택 앞 {@value #STACK_TRACE_LINES}줄
 * @param receivedAt 받은 시각(처음이면 처음 · 마지막 받은 시각, 다시 받음이면 마지막 받은 시각)
 */
public record ReceivedDeadLetter(
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
    FailureType failureType,
    String exceptionClass,
    String exceptionMessage,
    String stackTraceHead,
    LocalDateTime receivedAt) {

    public static final int STACK_TRACE_LINES = 10;

    public static ReceivedDeadLetter from(Received letter) {
        return new ReceivedDeadLetter(letter.sourceTopic(), letter.sourcePartition(), letter.sourceOffset(),
            letter.messageKey(), letter.eventType(), letter.eventId(), letter.payload(), letter.producer(),
            letter.occurredAt(), letter.aggregateId(), letter.correlationId(), letter.orderId(), letter.failureType(),
            letter.exceptionClass(), letter.exceptionMessage(), letter.stackTrace(), letter.receivedAt());
    }

    public ReceivedDeadLetter {
        sourceTopic = cut(sourceTopic, 100);
        messageKey = cut(messageKey, 100);
        eventType = cut(eventType, 60);
        eventId = cut(eventId, 100);
        payload = withoutNul(payload);
        producer = cut(producer, 30);
        aggregateId = cut(aggregateId, 100);
        correlationId = cut(correlationId, 100);
        exceptionClass = cut(exceptionClass, 300);
        exceptionMessage = cut(exceptionMessage, 1000);
        stackTraceHead = stackTraceHead == null ? null
            : withoutNul(stackTraceHead).lines().limit(STACK_TRACE_LINES).collect(Collectors.joining("\n"));
    }

    private static String cut(String value, int maxLength) {
        String text = withoutNul(value);
        return text == null || text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    private static String withoutNul(String value) {
        return value == null ? null : value.replace('\u0000', '\uFFFD');
    }
}
