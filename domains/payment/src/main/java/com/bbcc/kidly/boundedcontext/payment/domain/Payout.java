package com.bbcc.kidly.boundedcontext.payment.domain;

import com.bbcc.kidly.global.entity.BaseTimeEntity;
import com.bbcc.kidly.shared.payment.domain.PayoutFailureCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 정산서 하나의 지급 결과. 정산서마다 한 번만 입금하고, 기록이 있는 정산서로 요청이 다시 오면 이 기록대로 결과를 다시 알린다.
 */
@Entity
@Table(schema = "payment", name = "payout")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payout extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long settlementId;

    // 판매자 ID(회원 ID)
    @Column(nullable = false)
    private Long sellerId;

    @Column(nullable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PayoutStatus status;

    // 입금한 원장 ID. 0원 지급이면 입금이 없어 null
    private Long ledgerId;

    private LocalDateTime paidAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private PayoutFailureCode failureCode;

    private LocalDateTime failedAt;

    private Payout(Long settlementId, Long sellerId, long amount, PayoutStatus status) {
        this.settlementId = settlementId;
        this.sellerId = sellerId;
        this.amount = amount;
        this.status = status;
    }

    public static Payout complete(Long settlementId, Long sellerId, long amount, Long ledgerId, LocalDateTime paidAt) {
        Payout payout = new Payout(settlementId, sellerId, amount, PayoutStatus.COMPLETED);
        payout.ledgerId = ledgerId;
        payout.paidAt = paidAt;
        return payout;
    }

    public static Payout fail(Long settlementId, Long sellerId, long amount, PayoutFailureCode failureCode,
        LocalDateTime failedAt) {
        Payout payout = new Payout(settlementId, sellerId, amount, PayoutStatus.FAILED);
        payout.failureCode = failureCode;
        payout.failedAt = failedAt;
        return payout;
    }
}
