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
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.Assert;

/** 판매자별 월 정산서. 이번 이슈에서는 상태 모델까지만 구현하고 실제 지급은 수행하지 않는다. */
@Entity
@Table(
        name = "settlement_statement",
        schema = "settlement",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_settlement_statement_seller_period",
                columnNames = {"seller_id", "period_start_on"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SettlementStatement extends BaseTimeEntity {

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

    @Column(name = "total_fee_amount", nullable = false)
    private Long totalFeeAmount;

    @Column(name = "total_settlement_amount", nullable = false)
    private Long totalSettlementAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SettlementStatementStatus status;

    private SettlementStatement(SettlementStatementCreateInfo info) {
        this.sellerId = positive(info.sellerId(), "sellerId");
        this.periodStartOn = requireNonNull(info.periodStartOn(), "periodStartOn은 필수입니다");
        this.periodEndOn = requireNonNull(info.periodEndOn(), "periodEndOn은 필수입니다");
        Assert.isTrue(periodStartOn.getDayOfMonth() == 1, "월 정산 시작일은 해당 월의 1일이어야 합니다");
        Assert.isTrue(periodEndOn.equals(periodStartOn.withDayOfMonth(periodStartOn.lengthOfMonth())),
                "월 정산 종료일은 해당 월의 마지막 날이어야 합니다");
        this.totalSalesAmount = nonNegative(info.totalSalesAmount(), "totalSalesAmount");
        this.totalFeeAmount = nonNegative(info.totalFeeAmount(), "totalFeeAmount");
        this.totalSettlementAmount = nonNegative(info.totalSettlementAmount(), "totalSettlementAmount");
        Assert.isTrue(totalFeeAmount <= totalSalesAmount, "수수료가 총 판매금액을 초과할 수 없습니다");
        Assert.isTrue(totalSettlementAmount == totalSalesAmount - totalFeeAmount,
                "총 정산금액은 총 판매금액에서 총 수수료를 뺀 값이어야 합니다");
        this.status = SettlementStatementStatus.CREATED;
    }

    public static SettlementStatement create(SettlementStatementCreateInfo info) {
        return new SettlementStatement(requireNonNull(info, "정산서 생성 정보는 필수입니다"));
    }

    public void confirm() {
        Assert.state(status == SettlementStatementStatus.CREATED, "생성된 정산서만 확정할 수 있습니다");
        this.status = SettlementStatementStatus.CONFIRMED;
    }

    public void markPaid() {
        Assert.state(status == SettlementStatementStatus.CONFIRMED, "확정된 정산서만 지급 완료 처리할 수 있습니다");
        this.status = SettlementStatementStatus.PAID;
    }

    public void markPaymentFailed() {
        Assert.state(status == SettlementStatementStatus.CONFIRMED, "확정된 정산서만 지급 실패 처리할 수 있습니다");
        this.status = SettlementStatementStatus.PAYMENT_FAILED;
    }

    private static Long positive(Long value, String name) {
        requireNonNull(value, name + "은 필수입니다");
        Assert.isTrue(value > 0L, name + "은 0보다 커야 합니다");
        return value;
    }

    private static Long nonNegative(Long value, String name) {
        requireNonNull(value, name + "은 필수입니다");
        Assert.isTrue(value >= 0L, name + "은 0 이상이어야 합니다");
        return value;
    }
}
