package com.bbcc.kidly.boundedcontext.payment.domain;

import com.bbcc.kidly.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 충전 취소 요청. PG 취소가 실패하면 기록을 남기고 새 요청을 만든다.
 */
@Entity
@Table(schema = "payment", name = "charge_cancellation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChargeCancellation extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "charge_id")
    private Charge charge;

    @Column(nullable = false)
    private long amount;

    // 토스 취소 API의 cancelReason
    @Column(nullable = false)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChargeCancellationStatus status;

    private LocalDateTime cancelledAt;

    // PG 취소 실패 시 토스 오류 코드. Charge.FAILURE_CODE_LENGTH자를 넘으면 잘라 남긴다
    @Column(length = Charge.FAILURE_CODE_LENGTH)
    private String failureCode;

    private ChargeCancellation(Charge charge, long amount, String reason) {
        this.charge = charge;
        this.amount = amount;
        this.reason = reason;
        this.status = ChargeCancellationStatus.REQUESTED;
    }

    static ChargeCancellation request(Charge charge, long amount, String reason) {
        return new ChargeCancellation(charge, amount, reason);
    }

    void complete(LocalDateTime cancelledAt) {
        this.cancelledAt = cancelledAt;
        this.status = ChargeCancellationStatus.COMPLETED;
    }

    void fail(String failureCode) {
        this.failureCode = Charge.limitFailureCode(failureCode);
        this.status = ChargeCancellationStatus.FAILED;
    }
}
