package com.bbcc.kidly.boundedcontext.payment.in.dto;

import com.bbcc.kidly.boundedcontext.payment.app.dto.OrderItemRefundRetry;
import java.util.Optional;

/**
 * @param resolved 환불돼 기록이 지워졌으면 true
 * @param retry 또 실패했으면 바뀐 기록. 환불됐으면 null
 */
public record RefundRetryResultResponse(boolean resolved, RefundRetryResponse retry) {

    public static RefundRetryResultResponse from(Optional<OrderItemRefundRetry> remaining) {
        return remaining
            .map(retry -> new RefundRetryResultResponse(false, RefundRetryResponse.from(retry)))
            .orElse(new RefundRetryResultResponse(true, null));
    }
}
