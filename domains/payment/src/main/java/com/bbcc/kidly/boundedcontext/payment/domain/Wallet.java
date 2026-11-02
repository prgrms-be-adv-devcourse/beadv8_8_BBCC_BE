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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원의 예치금 지갑. 회원당 하나다.
 * 잔액을 바꿀 때는 리포지토리에서 비관적 락(SELECT ... FOR UPDATE)으로 읽어 동시 변경을 순서대로 처리한다.
 * 회원이 탈퇴하면 동결한다(#670). 동결해도 잔액은 그대로 두고 입금도 받는다. 돈이 나가는 새 요청(충전 · 주문 결제)을 막는 것은 서비스가 한다.
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

    // 회원 탈퇴로 동결한 시각. null이면 동결되지 않았다
    private LocalDateTime frozenAt;

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

    /**
     * 지갑을 동결한다. 이미 동결된 지갑이면 처음 동결한 시각을 그대로 둔다.
     *
     * @return 이번에 동결했으면 true, 이미 동결돼 있었으면 false
     */
    public boolean freeze(LocalDateTime now) {
        if (isFrozen()) {
            return false;
        }
        this.frozenAt = now;
        return true;
    }

    public boolean isFrozen() {
        return frozenAt != null;
    }

    public boolean canWithdraw(long amount) {
        return balance >= amount;
    }

    public void withdraw(long amount) {
        Amounts.requirePositive(amount);
        if (!canWithdraw(amount)) {
            throw new BusinessException(PaymentErrorCode.INSUFFICIENT_BALANCE);
        }
        this.balance -= amount;
    }
}
