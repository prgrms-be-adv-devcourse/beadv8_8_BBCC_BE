package com.bbcc.kidly.boundedcontext.payment.app.dto;

import com.bbcc.kidly.boundedcontext.payment.domain.Charge;
import java.time.LocalDateTime;

/**
 * 관리자 멈춘 충전 목록의 한 줄. 토스 상점관리자에서 찾을 수 있게 주문번호를 준다. paymentKey는 주지 않는다.
 *
 * @param memberId 지갑 주인. 지갑을 찾지 못하면 null
 * @param tossOrderId 토스에 넘긴 주문번호
 * @param status 충전 상태 이름(ChargeStatus)
 * @param updatedAt 마지막으로 상태가 바뀐 시각. 승인 중이면 승인을 시작한 시각이다
 */
public record StuckCharge(Long chargeId, Long walletId, Long memberId, String tossOrderId, long amount,
                          String status, LocalDateTime createdAt, LocalDateTime updatedAt) {

    public static StuckCharge of(Charge charge, Long memberId) {
        return new StuckCharge(charge.getId(), charge.getWalletId(), memberId, charge.getTossOrderId(),
            charge.getAmount(), charge.getStatus().name(), charge.getCreatedAt(), charge.getUpdatedAt());
    }
}
