package com.bbcc.kidly.boundedcontext.payment.in.dto;

import com.bbcc.kidly.boundedcontext.payment.app.dto.WalletBalanceResult;

/**
 * @param balance 남은 예치금(원)
 */
public record WalletBalanceResponse(long balance) {

    public static WalletBalanceResponse from(WalletBalanceResult result) {
        return new WalletBalanceResponse(result.balance());
    }
}
