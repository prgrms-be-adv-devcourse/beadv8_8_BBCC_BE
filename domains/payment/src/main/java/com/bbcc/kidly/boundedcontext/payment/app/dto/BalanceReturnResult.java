package com.bbcc.kidly.boundedcontext.payment.app.dto;

import java.time.LocalDateTime;

/**
 * 잔액 환급 결과.
 *
 * @param balance 환급 뒤 잔액(0)
 */
public record BalanceReturnResult(Long balanceReturnId, Long walletId, Long memberId, long amount, long balance,
                                  Long ledgerId, LocalDateTime returnedAt) {
}
