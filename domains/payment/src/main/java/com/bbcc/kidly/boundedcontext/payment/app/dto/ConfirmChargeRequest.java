package com.bbcc.kidly.boundedcontext.payment.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 충전 승인 요청. 토스 결제창이 성공 리다이렉트로 넘긴 값을 그대로 보낸다.
 *
 * @param orderId 충전을 요청할 때 받은 토스 주문번호
 * @param amount 결제창에서 결제한 금액(원). 요청한 충전 금액과 같아야 한다
 */
public record ConfirmChargeRequest(@NotBlank String paymentKey, @NotBlank String orderId, @NotNull Long amount) {
}
