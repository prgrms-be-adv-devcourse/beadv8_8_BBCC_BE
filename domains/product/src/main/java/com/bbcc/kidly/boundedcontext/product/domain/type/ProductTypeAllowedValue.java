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
        var allowedValue = new ProductTypeAllowedValue();
        allowedValue.productType = productType;
        allowedValue.attributeValue = attributeValue;
        return allowedValue;
    }
}
