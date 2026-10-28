package com.bbcc.kidly.boundedcontext.payment.domain;

import com.bbcc.kidly.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 지갑 원장. 잔액이 바뀔 때마다 한 줄씩 쌓고, 한 번 기록하면 바꾸지 않는다.
 * 같은 원인(type + ref_id)은 한 번만 기록해 이벤트를 두 번 받아도 잔액이 두 번 바뀌지 않게 한다.
 */
@Entity
@Table(
    schema = "payment",
    name = "wallet_ledger",
    uniqueConstraints = @UniqueConstraint(columnNames = {"type", "ref_id"}),
    indexes = @Index(columnList = "wallet_id, id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WalletLedger extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long walletId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WalletLedgerType type;

    // 입금은 양수, 출금은 음수
    @Column(nullable = false)
    private long amount;

    @Column(nullable = false)
    private long balanceAfter;

    // type에 따라 charge, charge_cancellation(CHARGE_CANCEL · CHARGE_CANCEL_REVERT), payment, refund, settlement의 ID
    @Column(nullable = false)
    private Long refId;

    private WalletLedger(Long walletId, WalletLedgerType type, long amount, long balanceAfter, Long refId) {
        this.walletId = walletId;
        this.type = type;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.refId = refId;
    }

    /**
     * @param amount 양수 금액. 출금 거래면 음수로 바꿔 기록한다
     */
    public static WalletLedger record(
        Long walletId, WalletLedgerType type, long amount, long balanceAfter, Long refId) {
        Amounts.requirePositive(amount);
        long signedAmount = type.isDeposit() ? amount : -amount;
        return new WalletLedger(walletId, type, signedAmount, balanceAfter, refId);
    }
}
