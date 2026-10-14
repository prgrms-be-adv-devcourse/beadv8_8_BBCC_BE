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

/**
 * 재고를 조정한 내역이다. 만든 뒤에는 바꾸지 않는다.
 */
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

    // 조정한 판매자 회원 ID다. 다른 도메인의 ID라 값으로만 가리킨다
    @Column(nullable = false)
    private Long adjustedBy;

    // 재고를 열 때 InventoryOpening만 만든다
    static InventoryAdjustmentRecord create(
            ProductOption productOption, int deltaQty, InventoryAdjustmentReason reason, Long adjustedBy) {
        InventoryAdjustmentRecord record = new InventoryAdjustmentRecord();
        record.productOption = productOption;
        record.deltaQty = deltaQty;
        record.reason = reason;
        record.adjustedBy = adjustedBy;
        return record;
    }
}
