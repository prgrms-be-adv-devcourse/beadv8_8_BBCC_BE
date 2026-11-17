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
@Table(schema = "product", name = "inventory_deduction")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InventoryDeduction extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long orderId;

    @Column(nullable = false, unique = true)
    private Long orderItemId;

    @Getter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_option_id")
    private ProductOption productOption;

    @Column(nullable = false)
    private int qty;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InventoryDeductionStatus status;

    public Long getProductOptionId() {
        return productOption.getId();
    }

    static InventoryDeduction deducted(long orderId, InventoryDeductionRequest.Item item) {
        return create(orderId, item, InventoryDeductionStatus.DEDUCTED);
    }

    static InventoryDeduction failed(long orderId, InventoryDeductionRequest.Item item) {
        return create(orderId, item, InventoryDeductionStatus.FAILED);
    }

    private static InventoryDeduction create(
            long orderId, InventoryDeductionRequest.Item item, InventoryDeductionStatus status) {
        var deduction = new InventoryDeduction();
        deduction.orderId = orderId;
        deduction.orderItemId = item.orderItemId();
        deduction.productOption = item.option();
        deduction.qty = item.qty();
        deduction.status = status;
        return deduction;
    }
}
