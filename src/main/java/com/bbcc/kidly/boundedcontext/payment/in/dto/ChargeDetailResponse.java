package com.bbcc.kidly.boundedcontext.payment.in.dto;

import com.bbcc.kidly.boundedcontext.payment.app.dto.ChargeDetail;
import java.time.LocalDateTime;

/**
 * @param approvedAt 승인되기 전이면 null
 */
public record ChargeDetailResponse(Long chargeId, String orderId, long amount, String status,
                                   LocalDateTime approvedAt, LocalDateTime createdAt) {

    public static ChargeDetailResponse from(ChargeDetail detail) {
        return new ChargeDetailResponse(detail.chargeId(), detail.orderId(), detail.amount(), detail.status(),
            detail.approvedAt(), detail.createdAt());
    }
}
