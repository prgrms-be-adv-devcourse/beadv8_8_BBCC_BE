package com.bbcc.kidly.boundedcontext.payment.app.dto;

import com.bbcc.kidly.boundedcontext.payment.domain.WalletLedger;
import java.time.LocalDateTime;

/**
 * 입출금 내역(원장)의 한 줄.
 *
 * @param type 거래 종류 이름(WalletLedgerType)
 * @param amount 입금은 양수, 출금은 음수(원)
 * @param balanceAfter 거래 뒤 잔액(원)
 * @param refId type에 따라 충전 · 충전 취소 · 결제 · 환불 · 정산 ID
 */
public record WalletLedgerEntry(Long ledgerId, String type, long amount, long balanceAfter, Long refId,
                                LocalDateTime createdAt) {

    public static WalletLedgerEntry from(WalletLedger ledger) {
        return new WalletLedgerEntry(ledger.getId(), ledger.getType().name(), ledger.getAmount(),
            ledger.getBalanceAfter(), ledger.getRefId(), ledger.getCreatedAt());
    }
}
