package com.bbcc.kidly.boundedcontext.payment.app.dto;

import com.bbcc.kidly.boundedcontext.payment.domain.Wallet;
import java.time.LocalDateTime;

/**
 * 관리자 지갑 목록의 한 줄.
 *
 * @param balance 남은 예치금(원)
 * @param frozenAt 회원 탈퇴로 동결한 시각. 동결되지 않았으면 null
 */
public record WalletSummary(Long walletId, Long memberId, long balance, LocalDateTime frozenAt) {

    public static WalletSummary from(Wallet wallet) {
        return new WalletSummary(wallet.getId(), wallet.getMemberId(), wallet.getBalance(), wallet.getFrozenAt());
    }
}
