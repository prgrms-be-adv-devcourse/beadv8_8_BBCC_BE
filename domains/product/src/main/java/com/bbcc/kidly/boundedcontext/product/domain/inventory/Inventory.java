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

    public Long getProductOptionId() {
        return productOption.getId();
    }

    static Inventory create(ProductOption productOption, int availableQty) {
        // 판매 가능 수량은 0 미만이 될 수 없다 (INV-01)
        if (availableQty < 0) {
            throw new IllegalArgumentException("판매 가능 수량은 0보다 작을 수 없습니다");
        }
        var inventory = new Inventory();
        inventory.productOption = productOption;
        inventory.availableQty = availableQty;
        return inventory;
    }
}
