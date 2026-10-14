package com.bbcc.kidly.boundedcontext.product.domain.type;

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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 상품 유형이 항목 하나를 어떻게 쓰는지 정한 설정이다(역할, 필수 여부, 순서 등).
 */
@Entity
@Table(
    schema = "product",
    name = "product_type_attribute",
    uniqueConstraints = @UniqueConstraint(columnNames = {"product_type_id", "attribute_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductTypeAttribute extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Getter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_type_id")
    private ProductType productType;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attribute_id")
    private Attribute attribute;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AttributeRole role;

    @Column(name = "is_required", nullable = false)
    private boolean required;

    @Column(name = "is_all_values_allowed", nullable = false)
    private boolean allValuesAllowed;

    @Column(nullable = false)
    private int sortOrder;

    @Column(name = "is_size_role", nullable = false)
    private boolean sizeRole;

    @Column(name = "is_price_affecting", nullable = false)
    private boolean priceAffecting;

    static ProductTypeAttribute create(ProductType productType, ProductType.LinkInfo info, int sortOrder) {
        ProductTypeAttribute link = new ProductTypeAttribute();
        link.productType = productType;
        link.attribute = info.attribute();
        link.role = info.role();
        link.required = info.required();
        link.allValuesAllowed = info.allValuesAllowed();
        link.sortOrder = sortOrder;
        link.sizeRole = info.sizeRole();
        link.priceAffecting = info.priceAffecting();
        return link;
    }
}
