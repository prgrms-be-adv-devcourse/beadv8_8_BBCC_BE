package com.bbcc.kidly.boundedcontext.payment.app.dto;

import com.bbcc.kidly.boundedcontext.payment.domain.OrderCancellationRetry;

/**
 * 다시 처리할 주문 취소 하나.
 */
public record OrderCancellationRetryTarget(Long retryId, OrderCancelCommand command) {

    public static OrderCancellationRetryTarget from(OrderCancellationRetry retry) {
        return new OrderCancellationRetryTarget(retry.getId(),
            new OrderCancelCommand(retry.getOrderId(), retry.getMemberId(), retry.getReason()));
    }
}
