package com.bbcc.kidly.boundedcontext.order.app.order.dto;

import com.bbcc.kidly.boundedcontext.order.domain.order.Order;
import java.time.LocalDateTime;

/**
 * 관리자 전체 주문 목록의 한 줄. 상태 · 사유는 enum 이름 문자열.
 *
 * @param cancelReason 취소 주문만, 나머지는 null
 * @param orderedAt 주문 시각(생성 시각, 내 주문 목록과 같은 출처)
 * @param itemCount 주문상품 수(행 수, 수량 합 아님)
 * @param cancelledAt 취소 시각(#1325): 주문이 CANCELLED가 된 상태 이력 시각(가장 최근). 취소 주문이 아니거나 이력이 없으면 null
 */
public record AdminOrderSummary(
    Long orderId,
    Long memberId,
    String status,
    String cancelReason,
    long totalAmount,
    LocalDateTime orderedAt,
    int itemCount,
    LocalDateTime cancelledAt) {

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
            order.getCreatedAt(), itemCount, null);
    }

    /**
     * 취소 시각을 붙인다(#1325, 목록이 페이지 단위로 한 번에 읽어 붙인다).
     */
    public AdminOrderSummary withCancelledAt(LocalDateTime cancelledAt) {
        return new AdminOrderSummary(orderId, memberId, status, cancelReason, totalAmount, orderedAt, itemCount,
            cancelledAt);
    }
}
