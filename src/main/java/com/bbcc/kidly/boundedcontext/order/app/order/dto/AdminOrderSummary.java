package com.bbcc.kidly.boundedcontext.order.app.order.dto;

import com.bbcc.kidly.boundedcontext.order.domain.order.Order;
import java.time.LocalDateTime;

/**
 * 관리자 전체 주문 목록의 한 줄. 상태 · 사유는 enum 이름 문자열.
 *
 * @param cancelReason 취소 주문만, 나머지는 null
 * @param orderedAt 주문 시각(생성 시각, 내 주문 목록과 같은 출처)
 * @param itemCount 주문상품 수(행 수, 수량 합 아님)
 */
public record AdminOrderSummary(
    Long orderId,
    Long memberId,
    String status,
    String cancelReason,
    long totalAmount,
    LocalDateTime orderedAt,
    int itemCount) {

    /**
     * 주문상품을 함께 읽은 주문으로 만든다.
     */
    public static AdminOrderSummary from(Order order) {
        return of(order, order.getItems().size());
    }

    /**
     * 주문상품을 읽지 못한 주문. 주문상품 수 0, 나머지 값은 그대로.
     */
    public static AdminOrderSummary withoutItems(Order order) {
        return of(order, 0);
    }

    private static AdminOrderSummary of(Order order, int itemCount) {
        return new AdminOrderSummary(order.getId(), order.getMemberId(), order.getStatus().name(),
            order.getCancelReason() == null ? null : order.getCancelReason().name(), order.getTotalAmount(),
            order.getCreatedAt(), itemCount);
    }
}
