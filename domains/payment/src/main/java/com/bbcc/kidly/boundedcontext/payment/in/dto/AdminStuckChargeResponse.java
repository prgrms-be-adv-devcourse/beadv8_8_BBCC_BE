package com.bbcc.kidly.boundedcontext.payment.in.dto;

import com.bbcc.kidly.boundedcontext.payment.app.dto.StuckCharge;
import java.time.LocalDateTime;

/**
 * 관리자 멈춘 충전 목록의 한 줄이자 종료한 충전.
 *
 * @param tossOrderId 토스 상점관리자에서 결제를 찾는 주문번호
 * @param status 충전 상태 이름. 목록은 CONFIRMING, 종료한 충전은 FAILED다
 */
public record AdminStuckChargeResponse(Long chargeId, Long walletId, Long memberId, String tossOrderId, long amount,
                                       String status, LocalDateTime createdAt, LocalDateTime updatedAt) {

    public static AdminStuckChargeResponse from(StuckCharge charge) {
        return new AdminStuckChargeResponse(charge.chargeId(), charge.walletId(), charge.memberId(),
            charge.tossOrderId(), charge.amount(), charge.status(), charge.createdAt(), charge.updatedAt());
    }
}
