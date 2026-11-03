package com.bbcc.kidly.boundedcontext.payment.app.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 잔액 환급 요청(#731).
 *
 * @param amount 관리자가 목록에서 본 잔액(원). 지금 잔액과 같아야 한다(그사이 입금 · 이미 처리한 경우를 막는다)
 */
public record ReturnBalanceRequest(@NotNull @Positive Long amount) {
}
