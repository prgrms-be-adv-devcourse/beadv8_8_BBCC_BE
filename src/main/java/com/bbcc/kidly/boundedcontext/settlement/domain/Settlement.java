package com.bbcc.kidly.boundedcontext.settlement.domain;

import static java.util.Objects.requireNonNull;

import com.bbcc.kidly.global.entity.BaseTimeEntity;
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
 * 판매자 한 명의 정산 기간 정산서. 판매 합계에서 수수료를 뺀 금액의 지급을 요청하고 결제의 지급 결과를 반영한다.
 */
@Entity
@Table(
    name = "settlement", schema = "settlement",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_settlement_seller_period", columnNames = {"seller_id", "period_start_on", "period_end_on"}))
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

    // 수수료 = 판매 합계 × 수수료율, 원 미만 버림. 만들면 지급 요청 상태(COMPLETED)다
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

    // 0 이상 1 이하, 소수점 넷째 자리까지
    public static void validateFeeRate(BigDecimal feeRate) {
        requireNonNull(feeRate, "feeRate는 필수입니다");
        Assert.isTrue(
            feeRate.signum() >= 0 && feeRate.compareTo(BigDecimal.ONE) <= 0, "수수료율은 0 이상 1 이하여야 합니다");
        Assert.isTrue(
            feeRate.stripTrailingZeros().scale() <= FEE_RATE_SCALE, "수수료율은 소수점 넷째 자리까지입니다");
    }

    // 결제가 같은 결과를 다시 보내도 처음 반영한 지급 시각을 유지한다
    public void markPaid(LocalDateTime paidAt) {
        requireNonNull(paidAt, "paidAt은 필수입니다");
        if (status == SettlementStatus.PAID) {
            return;
        }
        Assert.state(status == SettlementStatus.COMPLETED, "지급을 요청한 정산서만 지급 완료할 수 있습니다");
        this.status = SettlementStatus.PAID;
        this.paidAt = paidAt;
    }

    // 결제가 같은 결과를 다시 보내도 처음 반영한 사유를 유지한다
    public void markPaymentFailed(String failReason) {
        Assert.hasText(failReason, "failReason은 필수입니다");
        Assert.isTrue(failReason.length() <= FAIL_REASON_MAX_LENGTH, "failReason은 50자 이하여야 합니다");
        if (status == SettlementStatus.FAILED) {
            return;
        }
        Assert.state(status == SettlementStatus.COMPLETED, "지급을 요청한 정산서만 지급 실패할 수 있습니다");
        this.status = SettlementStatus.FAILED;
        this.failReason = failReason;
    }

    // 결제가 보낸 지급 결과가 이 정산서의 판매자 · 금액과 같은지
    public boolean isPayoutOf(Long sellerId, long amount) {
        return requireNonNull(sellerId, "sellerId는 필수입니다").equals(this.sellerId) && amount == settlementAmount;
    }
}
