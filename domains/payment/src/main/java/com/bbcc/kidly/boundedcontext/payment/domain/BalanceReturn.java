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
 * 탈퇴 회원 지갑의 잔액 환급 기록(#731). 운영자가 시스템 밖에서 돌려줬다고 보고 지갑 잔액을 정리한 일이다.
 * 실제 송금은 하지 않는다(토스는 테스트 결제). 원장 BALANCE_RETURN의 ref_id가 이 기록의 ID다.
 */
@Entity
@Table(schema = "payment", name = "balance_return")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BalanceReturn extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long walletId;

    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false)
    private long amount;

    // 처리한 관리자(회원 ID)
    @Column(nullable = false)
    private Long returnedBy;

    private BalanceReturn(Long walletId, Long memberId, long amount, Long returnedBy) {
        this.walletId = walletId;
        this.memberId = memberId;
        this.amount = amount;
        this.returnedBy = returnedBy;
    }

    public static BalanceReturn of(Wallet wallet, long amount, Long returnedBy) {
        Amounts.requirePositive(amount);
        return new BalanceReturn(wallet.getId(), wallet.getMemberId(), amount, returnedBy);
    }
}
