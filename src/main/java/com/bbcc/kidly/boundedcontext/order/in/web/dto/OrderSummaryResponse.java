package com.bbcc.kidly.boundedcontext.order.in.web.dto;

import com.bbcc.kidly.boundedcontext.order.app.order.dto.OrderSummary;
import java.time.LocalDateTime;

/**
 * 주문 목록의 한 줄(기획서 6.1 예시). ID는 문자열.
 *
 * @param representativeProductName 대표 상품명(주문상품 ID가 가장 작은 주문상품의 주문 당시 상품명). 없으면 null
 * @param itemCount 주문상품 수
 */
public record OrderSummaryResponse(
    String orderId,
    String status,
    long totalAmount,
    LocalDateTime orderedAt,
    String representativeProductName,
    int itemCount) {

    public static OrderSummaryResponse from(OrderSummary summary) {
        return new OrderSummaryResponse(String.valueOf(summary.orderId()), summary.status(), summary.totalAmount(),
            summary.orderedAt(), summary.representativeProductName(), summary.itemCount());
    }
}
