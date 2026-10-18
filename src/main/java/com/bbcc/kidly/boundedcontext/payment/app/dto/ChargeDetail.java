package com.bbcc.kidly.boundedcontext.payment.app.dto;

import com.bbcc.kidly.boundedcontext.payment.domain.Charge;
import java.time.LocalDateTime;

/**
 * @param status 충전 상태 이름(ChargeStatus)
 * @param approvedAt 승인되기 전이면 null
 */
public record ChargeDetail(Long chargeId, String orderId, long amount, String status,
                           LocalDateTime approvedAt, LocalDateTime expiresAt, LocalDateTime createdAt) {

    public static ChargeDetail from(Charge charge) {
        return new ChargeDetail(charge.getId(), charge.getTossOrderId(), charge.getAmount(), charge.getStatus().name(),
            charge.getApprovedAt(), charge.getExpiresAt(), charge.getCreatedAt());
    }
}
