package com.bbcc.kidly.boundedcontext.payment.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 결제창이 끝나지 않은 충전을 실패로 바꾸는 요청. 토스 결제창이 실패 리다이렉트로 넘긴 값을 그대로 보낸다.
 *
 * @param orderId 충전을 요청할 때 받은 토스 주문번호
 * @param code 토스 결제창 오류 코드(예: PAY_PROCESS_CANCELED). 충전의 실패 코드로 남는다
 */
public record AbandonChargeRequest(@NotBlank String orderId, @NotBlank @Size(max = 50) String code) {
}
