package com.bbcc.kidly.boundedcontext.order.app.deadletter.dto;

import com.bbcc.kidly.boundedcontext.order.app.deadletter.DeadLetterHandling;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderDeadLetterRepository.DeadLetter;
import java.time.LocalDateTime;

/**
 * 관리자 DLT 메시지 목록 한 줄(#601): 받은 시각 · 실패 유형 · eventType · 관련 주문 · 받은 횟수 · 상태.
 *
 * @param paymentEvent 결제 토픽에서 온 메시지(목록 맨 앞, #632)
 * @param sourceTopic 원래 토픽(MCP가 받은 eventType을 거를 때 쓴다, #925. 관리자 목록 응답에는 없다)
 */
public record OrderDeadLetterSummary(
    Long deadLetterId,
    LocalDateTime lastReceivedAt,
    String failureType,
    String eventType,
    Long orderId,
    int receivedCount,
    String status,
    boolean paymentEvent,
    String sourceTopic) {

    public static OrderDeadLetterSummary from(DeadLetter letter) {
        return new OrderDeadLetterSummary(letter.id(), letter.lastReceivedAt(), letter.failureType().name(),
            letter.eventType(), letter.orderId(), letter.receivedCount(), letter.status().name(),
            DeadLetterHandling.isPaymentEvent(letter), letter.sourceTopic());
    }
}
