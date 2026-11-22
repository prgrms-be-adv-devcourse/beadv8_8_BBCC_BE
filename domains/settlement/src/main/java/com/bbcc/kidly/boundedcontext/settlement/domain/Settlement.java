package com.bbcc.kidly.boundedcontext.settlement.domain;

import static java.util.Objects.requireNonNull;

import com.bbcc.kidly.global.entity.BaseTimeEntity;
import com.bbcc.kidly.global.error.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.Assert;

/**
 * 판매자 · 종료일마다 하나다(실행 하나에 판매자당 정산서 하나).
 */
@Entity
@Table(
    name = "settlement", schema = "settlement",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_settlement_seller_period_end_on", columnNames = {"seller_id", "period_end_on"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Settlement extends BaseTimeEntity {

    private static final int FEE_RATE_SCALE = 4;
    private static final int FAIL_REASON_MAX_LENGTH = 50;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "period_start_on", nullable = false)
    private LocalDate periodStartOn;

    @Column(name = "period_end_on", nullable = false)
    private LocalDate periodEndOn;

    @Column(name = "total_sales_amount", nullable = false)
    private Long totalSalesAmount;

    // 이 정산서에 적용한 율. 설정값이 바뀌어도 이미 만든 정산서는 그대로다
    @Column(name = "fee_rate", nullable = false, precision = 5, scale = FEE_RATE_SCALE)
    private BigDecimal feeRate;

    @Column(name = "fee_amount", nullable = false)
    private Long feeAmount;

    @Column(name = "settlement_amount", nullable = false)
    private Long settlementAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SettlementStatus status;

    @Column(name = "completed_at", nullable = false)
    private LocalDateTime completedAt;

    // 지급 완료 전에는 null
    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    // 지급 실패 전에는 null
    @Column(name = "fail_reason", length = FAIL_REASON_MAX_LENGTH)
    private String failReason;

    @Version
    private Long version;

    private Settlement(SettlementCreateInfo info, long feeAmount, LocalDateTime now) {
        this.sellerId = info.sellerId();
        this.periodStartOn = info.periodStartOn();
        this.periodEndOn = info.periodEndOn();
        this.totalSalesAmount = info.totalSalesAmount();
        this.feeRate = info.feeRate().setScale(FEE_RATE_SCALE);
        this.feeAmount = feeAmount;
        this.settlementAmount = info.totalSalesAmount() - feeAmount;
        this.status = SettlementStatus.COMPLETED;
        this.completedAt = now;
    }

    public static Settlement create(SettlementCreateInfo info, LocalDateTime now) {
        requireNonNull(info, "info는 필수입니다");
        requireNonNull(now, "now는 필수입니다");
        Assert.isTrue(requireNonNull(info.sellerId(), "sellerId는 필수입니다") > 0, "sellerId는 0보다 커야 합니다");
        var periodStartOn = requireNonNull(info.periodStartOn(), "periodStartOn은 필수입니다");
        var periodEndOn = requireNonNull(info.periodEndOn(), "periodEndOn은 필수입니다");
        Assert.isTrue(!periodStartOn.isAfter(periodEndOn), "정산 시작일은 종료일보다 늦을 수 없습니다");
        var total = requireNonNull(info.totalSalesAmount(), "totalSalesAmount는 필수입니다");
        Assert.isTrue(total >= 0, "판매 합계는 0 이상이어야 합니다");
        var feeRate = info.feeRate();
        validateFeeRate(feeRate);
        var feeAmount = BigDecimal.valueOf(total).multiply(feeRate)
            .setScale(0, RoundingMode.DOWN).longValueExact();
        return new Settlement(info, feeAmount, now);
    }

    public static void validateFeeRate(BigDecimal feeRate) {
        requireNonNull(feeRate, "feeRate는 필수입니다");
        Assert.isTrue(
            feeRate.signum() >= 0 && feeRate.compareTo(BigDecimal.ONE) <= 0, "수수료율은 0 이상 1 이하여야 합니다");
        Assert.isTrue(
            feeRate.stripTrailingZeros().scale() <= FEE_RATE_SCALE, "수수료율은 소수점 넷째 자리까지입니다");
    }

    // 결제가 같은 결과를 다시 보내도 처음 반영한 지급 시각을 유지하고 false를 돌려준다
    public boolean markPaid(LocalDateTime paidAt) {
        requireNonNull(paidAt, "paidAt은 필수입니다");
        if (status == SettlementStatus.PAID) {
            return false;
        }
        Assert.state(status == SettlementStatus.COMPLETED, "지급을 요청한 정산서만 지급 완료할 수 있습니다");
        this.status = SettlementStatus.PAID;
        this.paidAt = paidAt;
        return true;
    }

    // 결제가 같은 결과를 다시 보내도 처음 반영한 사유를 유지하고 false를 돌려준다
    public boolean markPaymentFailed(String failReason) {
        Assert.hasText(failReason, "failReason은 필수입니다");
        Assert.isTrue(failReason.length() <= FAIL_REASON_MAX_LENGTH, "failReason은 50자 이하여야 합니다");
        if (status == SettlementStatus.FAILED) {
            return false;
        }
        Assert.state(status == SettlementStatus.COMPLETED, "지급을 요청한 정산서만 지급 실패할 수 있습니다");
        this.status = SettlementStatus.FAILED;
        this.failReason = failReason;
        return true;
    }

    // 지급 결과를 받지 못한 정산서(COMPLETED)만 같은 정산서 ID로 지급을 다시 요청할 수 있다
    public void validatePayoutRetryable() {
        if (status != SettlementStatus.COMPLETED) {
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_NOT_RETRYABLE);
        }
    }

    public boolean isPayoutOf(Long sellerId, long amount) {
        return requireNonNull(sellerId, "sellerId는 필수입니다").equals(this.sellerId) && amount == settlementAmount;
    }
}
