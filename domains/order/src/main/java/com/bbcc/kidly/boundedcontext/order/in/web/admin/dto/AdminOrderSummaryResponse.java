package com.bbcc.kidly.boundedcontext.order.in.web.admin.dto;

import com.bbcc.kidly.boundedcontext.order.app.order.dto.AdminOrderSummary;
import java.time.LocalDateTime;

/**
 * 관리자 전체 주문 목록의 한 줄. ID는 문자열(기획서 6.1).
 *
 * @param cancelReason 취소 주문만, 나머지는 null
 * @param itemCount 주문상품 수
 * @param cancelledAt 취소 시각(#1325, 한국 시각). 취소 주문이 아니면 null
 */
public record AdminOrderSummaryResponse(
    String orderId,
    String memberId,
    String status,
    String cancelReason,
    long totalAmount,
    LocalDateTime orderedAt,
    int itemCount,
    LocalDateTime cancelledAt) {

    public static AdminOrderSummaryResponse from(AdminOrderSummary summary) {
        return new AdminOrderSummaryResponse(String.valueOf(summary.orderId()), String.valueOf(summary.memberId()),
            summary.status(), summary.cancelReason(), summary.totalAmount(), summary.orderedAt(), summary.itemCount(),
            summary.cancelledAt());
    }
}
