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
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.Assert;

/**
 * 구매확정된 주문상품 1건의 정산 항목. 대기로 쌓였다가 정산서에 들어가면 SETTLED가 된다.
 * 정산서는 ID로만 가리킨다.
 */
@Entity
@Table(
    name = "settlement_item", schema = "settlement",
    uniqueConstraints = @UniqueConstraint(name = "uk_settlement_item_order_item_id", columnNames = "order_item_id"),
    indexes = @Index(
        name = "idx_settlement_item_seller_status_confirmed", columnList = "seller_id, status, confirmed_at"))
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

    @Column(name = "confirmed_at", nullable = false)
    private LocalDateTime confirmedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SettlementItemStatus status;

    // 정산서에 들어가기 전에는 null
    @Column(name = "settlement_id")
    private Long settlementId;

    @Version
    private Long version;

    private SettlementItem(SettlementItemCreateInfo info) {
        this.orderId = info.orderId();
        this.orderItemId = info.orderItemId();
        this.sellerId = info.sellerId();
        this.salesAmount = info.salesAmount();
        this.confirmedAt = info.confirmedAt();
        this.status = SettlementItemStatus.PENDING;
    }

    public static SettlementItem create(SettlementItemCreateInfo info) {
        requireNonNull(info, "info는 필수입니다");
        Assert.isTrue(requireNonNull(info.orderId(), "orderId는 필수입니다") > 0, "orderId는 0보다 커야 합니다");
        Assert.isTrue(
            requireNonNull(info.orderItemId(), "orderItemId는 필수입니다") > 0, "orderItemId는 0보다 커야 합니다");
        Assert.isTrue(requireNonNull(info.sellerId(), "sellerId는 필수입니다") > 0, "sellerId는 0보다 커야 합니다");
        Assert.isTrue(
            requireNonNull(info.salesAmount(), "salesAmount는 필수입니다") >= 0, "판매금액은 0 이상이어야 합니다");
        requireNonNull(info.confirmedAt(), "confirmedAt은 필수입니다");
        return new SettlementItem(info);
    }

    public void settle(Long settlementId) {
        Assert.isTrue(requireNonNull(settlementId, "settlementId는 필수입니다") > 0, "settlementId는 0보다 커야 합니다");
        Assert.state(status == SettlementItemStatus.PENDING, "대기 중인 정산 항목만 정산할 수 있습니다");
        this.settlementId = settlementId;
        this.status = SettlementItemStatus.SETTLED;
    }

    // 같은 제외가 다시 와도 그대로 둔다. 정산서에 들어간 항목은 제외할 수 없다
    public void exclude() {
        if (status == SettlementItemStatus.EXCLUDED) {
            return;
        }
        Assert.state(status == SettlementItemStatus.PENDING, "정산된 항목은 제외할 수 없습니다");
        this.status = SettlementItemStatus.EXCLUDED;
    }
}
