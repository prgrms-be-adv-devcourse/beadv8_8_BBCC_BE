package com.bbcc.kidly.boundedcontext.payment.domain;

import com.bbcc.kidly.global.entity.BaseTimeEntity;
import com.bbcc.kidly.global.error.BusinessException;
import jakarta.persistence.CheckConstraint;
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
 * 회원의 예치금 지갑. 회원당 하나다.
 * 잔액을 바꿀 때는 리포지토리에서 비관적 락(SELECT ... FOR UPDATE)으로 읽어 동시 변경을 순서대로 처리한다.
 */
@Entity
@Table(
    schema = "payment",
    name = "wallet",
    check = @CheckConstraint(name = "ck_wallet_balance", constraint = "balance >= 0"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Wallet extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long memberId;

    // 남은 예치금(원). deposit·withdraw로만 바뀐다
    @Column(nullable = false)
    private long balance;

    private Wallet(Long memberId) {
        this.memberId = memberId;
    }

    public static Wallet create(Long memberId) {
        return new Wallet(memberId);
    }

    public void deposit(long amount) {
        Amounts.requirePositive(amount);
        this.balance += amount;
    }

    public void withdraw(long amount) {
        Amounts.requirePositive(amount);
        if (balance < amount) {
            throw new BusinessException(PaymentErrorCode.INSUFFICIENT_BALANCE);
        }
        this.balance -= amount;
    }
}
