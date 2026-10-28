package com.bbcc.kidly.boundedcontext.payment.in.dto;

import com.bbcc.kidly.boundedcontext.payment.app.dto.WalletLedgerEntry;
import java.time.LocalDateTime;

/**
 * @param amount 입금은 양수, 출금은 음수(원)
 * @param refId type에 따라 충전 · 충전 취소 · 결제 · 환불 · 정산 ID
 */
public record WalletLedgerResponse(Long ledgerId, String type, long amount, long balanceAfter, Long refId,
                                   LocalDateTime createdAt) {

    public static WalletLedgerResponse from(WalletLedgerEntry entry) {
        return new WalletLedgerResponse(entry.ledgerId(), entry.type(), entry.amount(), entry.balanceAfter(),
            entry.refId(), entry.createdAt());
    }
}
