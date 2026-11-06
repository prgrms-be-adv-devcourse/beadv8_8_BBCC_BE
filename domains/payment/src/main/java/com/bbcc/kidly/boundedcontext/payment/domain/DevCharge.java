package com.bbcc.kidly.boundedcontext.payment.domain;

import com.bbcc.kidly.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * local · test · dev에서 토스 결제창 없이 넣은 개발용 예치금 기록(#1010). 원장 DEV_CHARGE의 ref_id가 이 기록의 ID다.
 * 토스 충전(Charge)이 아니라서 취소 · 대사 대상이 아니다.
 */
@Entity
@Table(schema = "payment", name = "dev_charge")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DevCharge extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long walletId;

    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false)
    private long amount;

    private DevCharge(Long walletId, Long memberId, long amount) {
        this.walletId = walletId;
        this.memberId = memberId;
        this.amount = amount;
    }

    public static DevCharge of(Wallet wallet, long amount) {
        Amounts.requirePositive(amount);
        return new DevCharge(wallet.getId(), wallet.getMemberId(), amount);
    }
}
