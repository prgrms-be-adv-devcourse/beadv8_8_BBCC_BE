package com.bbcc.kidly.boundedcontext.payment.app.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 탈퇴로 동결된 지갑의 남은 잔액을 환급했다고 기록하는 관리자 요청.
 *
 * @param amount 관리자가 목록에서 본 잔액(원). 지금 잔액과 같아야 한다(그사이 입금 · 이미 처리한 경우를 막는다)
 */
public record ReturnBalanceRequest(@NotNull @Positive Long amount) {
}
