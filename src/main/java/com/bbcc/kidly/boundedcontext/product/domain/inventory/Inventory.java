package com.bbcc.kidly.boundedcontext.product.domain.inventory;

import com.bbcc.kidly.boundedcontext.product.domain.product.ProductOption;
import com.bbcc.kidly.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 옵션 하나에 재고 행 하나를 둔다.
 * 재고 행은 조건부 UPDATE로만 바꾸므로 version을 두지 않는다.
 */
@Entity
@Table(schema = "product", name = "inventory")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Inventory extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Getter(AccessLevel.NONE)
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_option_id", unique = true)
    private ProductOption productOption;

    @Column(nullable = false)
    private int availableQty;

    // 재고를 열 때 InventoryOpening만 만든다
    static Inventory create(ProductOption productOption, int availableQty) {
        Inventory inventory = new Inventory();
        inventory.productOption = productOption;
        inventory.availableQty = availableQty;
        return inventory;
    }
}
