package com.bbcc.kidly.boundedcontext.payment.app.dto;

/**
 * 개발용 예치금 충전 결과.
 *
 * @param balance 충전 뒤 잔액(원)
 */
public record DevChargeResult(Long devChargeId, Long ledgerId, long amount, long balance) {
}
