package com.bbcc.kidly.boundedcontext.order.app.deadletter.dto;

import com.bbcc.kidly.boundedcontext.order.domain.deadletter.OrderDeadLetter;
import java.time.LocalDateTime;

/**
 * 관리자 DLT 메시지 목록 한 줄(#601): 받은 시각 · 실패 유형 · eventType · 관련 주문 · 받은 횟수 · 상태.
 *
 * @param paymentEvent 결제 토픽에서 온 메시지(목록 맨 앞, #632)
 */
public record OrderDeadLetterSummary(
    Long deadLetterId,
    LocalDateTime lastReceivedAt,
    String failureType,
    String eventType,
    Long orderId,
    int receivedCount,
    String status,
    boolean paymentEvent) {

    public static OrderDeadLetterSummary from(OrderDeadLetter letter) {
        return new OrderDeadLetterSummary(letter.getId(), letter.getLastReceivedAt(), letter.getFailureType().name(),
            letter.getEventType(), letter.getOrderId(), letter.getReceivedCount(), letter.getStatus().name(),
            letter.isPaymentEvent());
    }
}
