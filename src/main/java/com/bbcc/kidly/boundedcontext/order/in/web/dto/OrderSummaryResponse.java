package com.bbcc.kidly.boundedcontext.order.in.web.dto;

import com.bbcc.kidly.boundedcontext.order.app.order.dto.OrderSummary;
import java.time.LocalDateTime;

/**
 * 주문 목록의 한 줄(기획서 6.1 예시). ID는 문자열.
 */
public record OrderSummaryResponse(String orderId, String status, long totalAmount, LocalDateTime orderedAt) {

    public static OrderSummaryResponse from(OrderSummary summary) {
        return new OrderSummaryResponse(String.valueOf(summary.orderId()), summary.status(), summary.totalAmount(),
            summary.orderedAt());
    }
}
