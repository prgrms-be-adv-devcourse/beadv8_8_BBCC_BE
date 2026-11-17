package com.bbcc.kidly.boundedcontext.payment.in.dto;

import com.bbcc.kidly.boundedcontext.payment.app.dto.OrderItemRefundRetry;
import java.time.LocalDateTime;
import java.util.List;

public record RefundRetryResponse(Long retryId, Long orderId, Long cancellationId, Long memberId, String reason,
                                  List<Item> items, long amount, int attemptCount, String lastError,
                                  LocalDateTime createdAt, LocalDateTime lastFailedAt) {

    public record Item(Long orderItemId, long amount) {
    }

    public static RefundRetryResponse from(OrderItemRefundRetry retry) {
        return new RefundRetryResponse(retry.retryId(), retry.orderId(), retry.cancellationId(), retry.memberId(),
            retry.reason(), retry.items().stream().map(item -> new Item(item.orderItemId(), item.amount())).toList(),
            retry.amount(), retry.attemptCount(), retry.lastError(), retry.createdAt(), retry.lastFailedAt());
    }
}
