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

    private String skuCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductOptionStatus saleStatus;

    @OneToMany(mappedBy = "productOption", cascade = CascadeType.ALL)
    @OrderBy("id ASC")
    private List<ProductOptionValue> values = new ArrayList<>();

    static ProductOption create(Product product, long price, String skuCode, List<AttributeValue> values) {
        validateSkuCode(skuCode);
        var option = new ProductOption();
        option.product = product;
        option.price = price;
        option.skuCode = skuCode;
        option.saleStatus = ProductOptionStatus.ON_SALE;
        for (AttributeValue value : values) {
            option.values.add(ProductOptionValue.create(option, value));
        }
        return option;
    }

    void replaceSkuAndPrice(String skuCode, long price) {
        validateSkuCode(skuCode);
        this.skuCode = skuCode;
        this.price = price;
    }

    // 요청 DTO의 @Pattern은 Unicode 공백 기준이라 isBlank()가 공백으로 보는 제어 문자(예: \u001C)를 통과시킨다
    private static void validateSkuCode(String skuCode) {
        if (skuCode != null && skuCode.isBlank()) {
            throw new IllegalArgumentException("SKU 코드는 공백만으로 넣을 수 없습니다");
        }
    }

    public boolean isPurchasable() {
        return notPurchasableReason().isEmpty();
    }

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

    public void validateAdjustableBy(Long sellerId) {
        product.validateOwner(sellerId);
        product.validateNotDelisted();
    }

    public boolean isArchived() {
        return saleStatus == ProductOptionStatus.ARCHIVED;
    }

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
