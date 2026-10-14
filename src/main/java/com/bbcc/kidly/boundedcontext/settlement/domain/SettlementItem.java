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
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.Assert;

/** 주문항목별 정산대상. 구매확정 이후 14일을 기다리고 정산 상태 전이를 관리한다. */
@Entity
@Table(
        name = "settlement_item",
        schema = "settlement",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_settlement_item_order_item_id",
                columnNames = "order_item_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SettlementItem extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "order_item_id", nullable = false)
    private Long orderItemId;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "sales_amount", nullable = false)
    private Long salesAmount;

    // 수수료 산정은 후속 업무에서 확정하므로 생성 시 null이다.
    @Column(name = "fee_amount")
    private Long feeAmount;

    @Column(name = "settlement_amount")
    private Long settlementAmount;

    @Column(name = "confirmed_at", nullable = false)
    private LocalDateTime confirmedAt;

    @Column(name = "eligible_at", nullable = false)
    private LocalDateTime eligibleAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SettlementItemStatus status;

    // 다른 Aggregate Root와 연결하되 저장·삭제는 전파하지 않는다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "settlement_statement_id",
            foreignKey = @ForeignKey(name = "fk_settlement_item_statement"))
    private SettlementStatement settlementStatement;

    private SettlementItem(SettlementItemCreateInfo info) {
        this.orderId = positive(info.orderId(), "orderId");
        this.orderItemId = positive(info.orderItemId(), "orderItemId");
        this.sellerId = positive(info.sellerId(), "sellerId");
        this.salesAmount = nonNegative(info.salesAmount(), "salesAmount");
        this.confirmedAt = requireNonNull(info.confirmedAt(), "confirmedAt은 필수입니다");
        this.eligibleAt = confirmedAt.plusDays(14);
        this.status = SettlementItemStatus.PENDING;
    }

    public static SettlementItem create(SettlementItemCreateInfo info) {
        return new SettlementItem(requireNonNull(info, "정산항목 생성 정보는 필수입니다"));
    }

    /** 구매확정 후 14일이 지났을 때만 READY로 변경한다. 중복 호출은 허용한다. */
    public void markReady(LocalDateTime now) {
        requireNonNull(now, "now는 필수입니다");
        if (status == SettlementItemStatus.READY) {
            return;
        }
        Assert.state(status == SettlementItemStatus.PENDING, "대기 중인 정산항목만 준비 상태가 될 수 있습니다");
        Assert.isTrue(!now.isBefore(eligibleAt), "구매확정 후 14일이 지나야 정산할 수 있습니다");
        this.status = SettlementItemStatus.READY;
    }

    /** 정산서에 반영되기 전 취소·환불로 정산 대상을 제외한다. */
    public void exclude() {
        if (status == SettlementItemStatus.EXCLUDED) {
            return;
        }
        Assert.state(
                status == SettlementItemStatus.PENDING || status == SettlementItemStatus.READY,
                "이미 정산서에 반영된 항목은 이 방식으로 제외할 수 없습니다");
        this.status = SettlementItemStatus.EXCLUDED;
    }

    /** 준비된 항목을 생성 상태의 정산서에 반영한다. SETTLED는 지급 완료가 아니다. */
    public void assignToStatement(SettlementStatement statement, Long feeAmount) {
        requireNonNull(statement, "정산서는 필수입니다");
        Assert.state(status == SettlementItemStatus.READY, "준비된 정산항목만 정산서에 반영할 수 있습니다");
        Assert.state(statement.getStatus() == SettlementStatementStatus.CREATED,
                "생성 상태의 정산서에만 정산항목을 추가할 수 있습니다");
        Assert.isTrue(sellerId.equals(statement.getSellerId()), "정산항목과 정산서의 판매자가 다릅니다");
        Assert.isTrue(!eligibleAt.toLocalDate().isBefore(statement.getPeriodStartOn())
                        && !eligibleAt.toLocalDate().isAfter(statement.getPeriodEndOn()),
                "정산 가능일이 정산서 기간에 속하지 않습니다");
        Long validatedFee = nonNegative(feeAmount, "feeAmount");
        Assert.isTrue(validatedFee <= salesAmount, "수수료가 판매금액을 초과할 수 없습니다");

        this.feeAmount = validatedFee;
        this.settlementAmount = salesAmount - validatedFee;
        this.settlementStatement = statement;
        this.status = SettlementItemStatus.SETTLED;
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
