package com.bbcc.kidly.boundedcontext.payment.in.dto;

import com.bbcc.kidly.boundedcontext.payment.app.dto.BalanceReturnResult;
import java.time.LocalDateTime;

public record BalanceReturnResponse(Long balanceReturnId, Long walletId, Long memberId, long amount, long balance,
                                    Long ledgerId, LocalDateTime returnedAt) {

    public static BalanceReturnResponse from(BalanceReturnResult result) {
        return new BalanceReturnResponse(result.balanceReturnId(), result.walletId(), result.memberId(),
            result.amount(), result.balance(), result.ledgerId(), result.returnedAt());
    }
}
