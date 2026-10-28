package com.bbcc.kidly.boundedcontext.payment.app.dto;

/**
 * 승인 중으로 바꾼 충전. 토스 승인을 호출할 때 쓴다.
 *
 * @param orderId 토스 주문번호. 승인 호출의 멱등 키로도 쓴다
 */
public record ChargeConfirmTarget(Long chargeId, String orderId, long amount) {
}
