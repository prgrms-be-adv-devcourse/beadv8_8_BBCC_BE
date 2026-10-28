package com.bbcc.kidly.boundedcontext.payment.app.dto;

import com.bbcc.kidly.boundedcontext.payment.domain.Wallet;

/**
 * @param balance 남은 예치금(원)
 */
public record WalletBalanceResult(long balance) {

    public static WalletBalanceResult from(Wallet wallet) {
        return new WalletBalanceResult(wallet.getBalance());
    }
}
