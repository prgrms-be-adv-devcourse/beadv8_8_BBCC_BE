package com.bbcc.kidly.boundedcontext.payment.in.dto;

import com.bbcc.kidly.boundedcontext.payment.app.dto.WalletSummary;
import java.time.LocalDateTime;

/**
 * 관리자 지갑 목록의 한 줄.
 *
 * @param balance 남은 예치금(원)
 * @param frozenAt 회원 탈퇴로 동결한 시각. 동결되지 않았으면 null
 */
public record AdminWalletResponse(Long walletId, Long memberId, long balance, LocalDateTime frozenAt) {

    public static AdminWalletResponse from(WalletSummary summary) {
        return new AdminWalletResponse(summary.walletId(), summary.memberId(), summary.balance(), summary.frozenAt());
    }
}
