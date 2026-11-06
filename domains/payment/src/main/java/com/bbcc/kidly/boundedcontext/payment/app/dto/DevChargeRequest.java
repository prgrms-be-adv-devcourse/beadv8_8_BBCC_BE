package com.bbcc.kidly.boundedcontext.payment.app.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 개발용 예치금 충전 요청.
 *
 * @param amount 넣을 금액(원). 1원부터 100만 원까지
 */
public record DevChargeRequest(@NotNull @Min(1) @Max(1_000_000) Long amount) {
}
