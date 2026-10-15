package com.bbcc.kidly.boundedcontext.product.domain.product;

import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeValue;
import com.bbcc.kidly.global.entity.BaseTimeEntity;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import com.bbcc.kidly.shared.product.domain.StockDeductionFailureCode;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 상품이 파는 옵션 하나다. 옵션 항목마다 값 하나를 가지며, 새 옵션은 판매중 상태로 만들어진다.
 * 이 행을 동시에 바꾸는 일은 상품의 version이 막으므로 이 행에는 version을 두지 않는다.
 */
@Entity
@Table(schema = "product", name = "product_option")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductOption extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Getter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(nullable = false)
    private long price;

    // 판매자가 붙이는 관리 코드다. 없으면 null이다
    private String skuCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductOptionStatus saleStatus;

    // DB에서 다시 읽어도 입력한 순서를 지키려고 id 순으로 읽는다
    @OneToMany(mappedBy = "productOption", cascade = CascadeType.ALL)
    @OrderBy("id ASC")
    private List<ProductOptionValue> values = new ArrayList<>();

    // 판매중 옵션을 만들고 옵션의 값마다 한 행을 입력 순서대로 붙인다
    static ProductOption create(Product product, long price, String skuCode, List<AttributeValue> values) {
        if (price <= 0) {
            throw new IllegalArgumentException("판매가는 0보다 커야 합니다");
        }
        // SKU 코드가 없으면 null로만 받는다. 요청 검사가 빈 코드를 먼저 400으로 막고, 여기서는 다른 경로를 막는다
        if (skuCode != null && skuCode.isBlank()) {
            throw new IllegalArgumentException("SKU 코드는 공백만으로 넣을 수 없습니다");
        }
        ProductOption option = new ProductOption();
        option.product = product;
        option.price = price;
        option.skuCode = skuCode;
        option.saleStatus = ProductOptionStatus.ON_SALE;
        for (AttributeValue value : values) {
            option.values.add(ProductOptionValue.create(option, value));
        }
        return option;
    }

    // 옵션을 살 수 있는지는 이 메서드 하나에서 판단한다. 상품과 옵션이 모두 판매중이어야 한다 (SALE-04)
    public boolean isPurchasable() {
        return notPurchasableReason().isEmpty();
    }

    // 옵션을 살 수 없는 이유를 돌려준다. 상품 상태를 먼저 보고, 살 수 있으면 빈 값이다 (SALE-04)
    public Optional<StockDeductionFailureCode> notPurchasableReason() {
        // 상품은 지연 로딩 프록시일 수 있고, getter를 부르면 프록시가 초기화돼 실제 상태를 돌려준다
        if (product.getStatus() != ProductStatus.ON_SALE) {
            return Optional.of(StockDeductionFailureCode.PRODUCT_NOT_ON_SALE);
        }
        if (saleStatus != ProductOptionStatus.ON_SALE) {
            return Optional.of(StockDeductionFailureCode.OPTION_NOT_ON_SALE);
        }
        return Optional.empty();
    }

    // 보관 옵션은 구매자 상세와 판매자 화면 어디에도 보이지 않는다 (SALE-06)
    public boolean isArchived() {
        return saleStatus == ProductOptionStatus.ARCHIVED;
    }

    // 옵션 이름은 저장하지 않고 옵션 값으로 만든다. 옵션 값은 유형의 항목 순서로 저장돼 있다 (OPT-09)
    public String getName() {
        List<String> parts = new ArrayList<>();
        for (ProductOptionValue value : values) {
            parts.add(value.getAttribute().getName() + ": " + value.getAttributeValue().getName());
        }
        return String.join(" / ", parts);
    }

    public List<ProductOptionValue> getValues() {
        return Collections.unmodifiableList(values);
    }
}
