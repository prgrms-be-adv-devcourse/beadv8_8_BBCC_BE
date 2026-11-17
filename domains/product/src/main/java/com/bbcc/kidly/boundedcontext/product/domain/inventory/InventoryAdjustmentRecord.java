package com.bbcc.kidly.boundedcontext.product.domain.inventory;

import com.bbcc.kidly.boundedcontext.product.domain.product.ProductOption;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(schema = "product", name = "inventory_adjustment_record")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InventoryAdjustmentRecord extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Getter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_option_id")
    private ProductOption productOption;

    @Column(nullable = false)
    private int deltaQty;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InventoryAdjustmentReason reason;

    @Column(nullable = false)
    private Long adjustedBy;

    public record AdjustInfo(ProductOption option, int deltaQty, InventoryAdjustmentReason reason, Long adjustedBy) {
    }

    public static InventoryAdjustmentRecord adjust(AdjustInfo info) {
        // INITIAL 기록은 옵션을 등록할 때 서버만 남긴다. 판매자가 INITIAL로 재고를 더하면 초기 재고와 입고를 구분할 수 없다 (INV-19)
        if (info.reason() == InventoryAdjustmentReason.INITIAL) {
            throw new IllegalArgumentException("재고 조정에 쓸 수 없는 사유입니다: INITIAL");
        }
        return create(info.option(), info.deltaQty(), info.reason(), info.adjustedBy());
    }

    public boolean changesSoldOut(int availableQtyAfter) {
        int availableQtyBefore = availableQtyAfter - deltaQty;
        return (availableQtyBefore <= 0) != (availableQtyAfter <= 0);
    }

    static InventoryAdjustmentRecord create(
            ProductOption productOption, int deltaQty, InventoryAdjustmentReason reason, Long adjustedBy) {
        // 수량이 바뀌지 않는 조정은 기록하지 않는다 (INV-18)
        if (deltaQty == 0) {
            throw new IllegalArgumentException("증감량은 0이 아니어야 합니다");
        }
        // 사유마다 허용하는 증감량 부호는 InventoryAdjustmentReason에 있다 (INV-18)
        if (!reason.allows(deltaQty)) {
            throw new IllegalArgumentException("사유 " + reason + "에 맞지 않는 증감량입니다: " + deltaQty);
        }
        var record = new InventoryAdjustmentRecord();
        record.productOption = productOption;
        record.deltaQty = deltaQty;
        record.reason = reason;
        record.adjustedBy = adjustedBy;
        return record;
    }
}
