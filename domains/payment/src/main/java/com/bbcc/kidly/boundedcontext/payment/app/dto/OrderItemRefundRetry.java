package com.bbcc.kidly.boundedcontext.payment.app.dto;

import com.bbcc.kidly.boundedcontext.payment.domain.OrderItemCancellationRetry;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 환불하지 못한 주문상품 취소 하나.
 *
 * @param lastError 실패 사유 코드(RefundFailureCode 이름, PAYMENT_NOT_FOUND, MEMBER_MISMATCH)
 * @param createdAt 처음 실패한 시각
 */
public record OrderItemRefundRetry(Long retryId, Long orderId, Long cancellationId, Long memberId, String reason,
                                   List<OrderItemCancelCommand.Item> items, long amount, int attemptCount,
                                   String lastError, LocalDateTime createdAt, LocalDateTime lastFailedAt) {

    public static OrderItemRefundRetry from(OrderItemCancellationRetry retry) {
        return new OrderItemRefundRetry(retry.getId(), retry.getOrderId(), retry.getCancellationId(),
            retry.getMemberId(), retry.getReason(), retry.getItems().stream()
                .map(item -> new OrderItemCancelCommand.Item(item.getOrderItemId(), item.getAmount()))
                .toList(), retry.getAmount(), retry.getAttemptCount(), retry.getLastError(), retry.getCreatedAt(),
            retry.getLastFailedAt());
    }

    public OrderItemCancelCommand command() {
        return new OrderItemCancelCommand(orderId, memberId, cancellationId, reason, items);
    }
}
