package com.bbcc.kidly.boundedcontext.product.domain.type;

import com.bbcc.kidly.global.entity.BaseTimeEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 상품 유형. 의류 · 신발처럼 상품이 어떤 항목을 옵션으로 고르고 상품 정보로 적는지 정한 정의.
 */
@Entity
@Table(schema = "product", name = "product_type")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductType extends BaseTimeEntity {

    /**
     * 유형에 연결할 항목 하나의 값. 허용 값은 항목 값 중 이 유형이 허용하는 것이다.
     */
    public record LinkInfo(
        Attribute attribute,
        AttributeRole role,
        boolean required,
        boolean allValuesAllowed,
        boolean sizeRole,
        boolean priceAffecting,
        List<AttributeValue> allowedValues) {
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @OneToMany(mappedBy = "productType", cascade = CascadeType.ALL)
    private List<ProductTypeAttribute> attributes = new ArrayList<>();

    public static ProductType define(String name, List<LinkInfo> links) {
        if (!hasRequiredOption(links)) {
            throw new IllegalArgumentException("유형에는 필수 옵션 항목이 하나 이상 있어야 합니다");
        }
        ProductType type = new ProductType();
        type.name = name;
        for (int i = 0; i < links.size(); i++) {
            type.attributes.add(ProductTypeAttribute.create(type, links.get(i), i + 1));
        }
        return type;
    }

    private static boolean hasRequiredOption(List<LinkInfo> links) {
        for (LinkInfo link : links) {
            if (link.role() == AttributeRole.OPTION && link.required()) {
                return true;
            }
        }
        return false;
    }

    public List<ProductTypeAttribute> getAttributes() {
        return Collections.unmodifiableList(attributes);
    }
}
