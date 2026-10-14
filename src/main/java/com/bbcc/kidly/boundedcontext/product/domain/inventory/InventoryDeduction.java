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
 * 주문상품 하나의 재고 차감 기록이다.
 */
@Entity
@Table(schema = "product", name = "inventory_deduction")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InventoryDeduction extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 주문 도메인의 ID다. 값으로만 가리킨다
    @Column(nullable = false)
    private Long orderId;

    // 같은 주문 생성됨 이벤트를 다시 받아도 재고를 두 번 차감하지 않도록 주문상품마다 기록을 하나만 둔다 (INV-10)
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

    // 다른 애그리거트라 옵션 객체는 열지 않고 ID만 돌려준다. 지연 로딩 프록시여도 ID는 초기화 없이 읽힌다
    public Long getProductOptionId() {
        return productOption.getId();
    }

    // 차감 요청(InventoryDeductionRequest)만 차감 기록을 만든다. 주문상품의 값은 요청이 이미 검증했다
    static InventoryDeduction deducted(long orderId, InventoryDeductionRequest.Item item) {
        return create(orderId, item, InventoryDeductionStatus.DEDUCTED);
    }

    static InventoryDeduction failed(long orderId, InventoryDeductionRequest.Item item) {
        return create(orderId, item, InventoryDeductionStatus.FAILED);
    }

    private static InventoryDeduction create(
            long orderId, InventoryDeductionRequest.Item item, InventoryDeductionStatus status) {
        InventoryDeduction deduction = new InventoryDeduction();
        deduction.orderId = orderId;
        deduction.orderItemId = item.orderItemId();
        deduction.productOption = item.option();
        deduction.qty = item.qty();
        deduction.status = status;
        return deduction;
    }
}
