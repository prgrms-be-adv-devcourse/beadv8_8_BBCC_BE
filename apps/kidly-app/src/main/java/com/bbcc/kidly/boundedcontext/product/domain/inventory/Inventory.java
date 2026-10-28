package com.bbcc.kidly.boundedcontext.product.domain.inventory;

import static java.util.Objects.requireNonNull;

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

    // 다른 애그리거트라 옵션 객체는 열지 않고 ID만 돌려준다. 지연 로딩 프록시여도 ID는 초기화 없이 읽힌다
    public Long getProductOptionId() {
        return productOption.getId();
    }

    // 재고를 열 때 InventoryOpening만 만든다
    static Inventory create(ProductOption productOption, int availableQty) {
        requireNonNull(productOption, "productOption은 필수입니다");
        // 판매 가능 수량은 0 미만이 될 수 없다 (INV-01)
        if (availableQty < 0) {
            throw new IllegalArgumentException("판매 가능 수량은 0보다 작을 수 없습니다");
        }
        Inventory inventory = new Inventory();
        inventory.productOption = productOption;
        inventory.availableQty = availableQty;
        return inventory;
    }
}
