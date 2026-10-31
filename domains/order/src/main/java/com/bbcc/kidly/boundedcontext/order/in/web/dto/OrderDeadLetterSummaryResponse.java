package com.bbcc.kidly.boundedcontext.order.in.web.dto;

import com.bbcc.kidly.boundedcontext.order.app.deadletter.dto.OrderDeadLetterSummary;
import java.time.LocalDateTime;

/**
 * 관리자 DLT 메시지 목록 한 줄(#601). ID는 문자열, 관련 주문이 없으면 orderId null.
 *
 * @param failureType READ_FAILED(읽기 실패) · STORE_FAILED(보관 실패) · PROCESS_FAILED(그 밖의 처리 실패)
 * @param receivedCount DLT로 온 횟수(같은 원래 메시지)
 * @param status OPEN(확인 대상) · REPROCESSED(재처리됨) · CLOSED(종료됨)
 */
public record OrderDeadLetterSummaryResponse(
    String deadLetterId,
    LocalDateTime lastReceivedAt,
    String failureType,
    String eventType,
    String orderId,
    int receivedCount,
    String status) {

    public static OrderDeadLetterSummaryResponse from(OrderDeadLetterSummary summary) {
        return new OrderDeadLetterSummaryResponse(String.valueOf(summary.deadLetterId()), summary.lastReceivedAt(),
            summary.failureType(), summary.eventType(), toText(summary.orderId()), summary.receivedCount(),
            summary.status());
    }

    private static String toText(Long id) {
        return id == null ? null : String.valueOf(id);
    }
}
