package com.bbcc.kidly.boundedcontext.payment.app.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 충전 요청. 금액 범위(1천 원 ~ 100만 원)는 Charge가 확인한다.
 *
 * @param amount 충전할 금액(원)
 */
public record ChargeRequest(@NotNull Long amount) {
}
