package com.bbcc.kidly.boundedcontext.product.domain.type;

import com.bbcc.kidly.global.entity.BaseTimeEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 상품 유형에서 고를 수 있도록 허용한 목록 값이다. 모든 값 허용이 아닌 항목만 이 값 중에서 고른다.
 */
@Entity
@Table(
    schema = "product",
    name = "product_type_allowed_value",
    uniqueConstraints = @UniqueConstraint(columnNames = {"product_type_id", "attribute_value_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductTypeAllowedValue extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Getter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_type_id")
    private ProductType productType;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attribute_value_id")
    private AttributeValue attributeValue;

    static ProductTypeAllowedValue create(ProductType productType, AttributeValue attributeValue) {
        ProductTypeAllowedValue allowedValue = new ProductTypeAllowedValue();
        allowedValue.productType = productType;
        allowedValue.attributeValue = attributeValue;
        return allowedValue;
    }
}
