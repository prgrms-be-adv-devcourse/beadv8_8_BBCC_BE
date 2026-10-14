package com.bbcc.kidly.boundedcontext.product.domain.type;

import static java.util.Objects.requireNonNull;

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
 * 상품 유형이다. 의류처럼 종류마다 옵션 항목과 정보 항목을 정해 둔다.
 */
@Entity
@Table(schema = "product", name = "product_type")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductType extends BaseTimeEntity {

    /**
     * 유형에 항목 하나를 연결하는 설정이다. allowedValues는 allValuesAllowed가 false일 때 고를 수 있는 값이다.
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

    @OneToMany(mappedBy = "productType", cascade = CascadeType.ALL)
    private List<ProductTypeAllowedValue> allowedValues = new ArrayList<>();

    public static ProductType define(String name, List<LinkInfo> links) {
        requireNonNull(name, "name은 필수입니다");
        requireNonNull(links, "links는 필수입니다");
        for (LinkInfo link : links) {
            requireNonNullValues(link);
        }
        if (name.isBlank()) {
            throw new IllegalArgumentException("유형 이름은 비어 있을 수 없습니다");
        }
        if (!hasRequiredOption(links)) {
            throw new IllegalArgumentException("유형에는 필수 옵션 항목이 하나 이상 있어야 합니다");
        }
        if (hasManySizeRoles(links)) {
            throw new IllegalArgumentException("사이즈 역할 항목은 하나만 둘 수 있습니다");
        }
        if (hasSizeRoleOnInfo(links)) {
            throw new IllegalArgumentException("사이즈 역할은 옵션 항목에만 둘 수 있습니다");
        }
        if (hasPriceAffectingOnInfo(links)) {
            throw new IllegalArgumentException("값에 따라 가격이 달라지는 항목은 옵션 항목에만 둘 수 있습니다");
        }
        if (hasTextOption(links)) {
            throw new IllegalArgumentException("직접 입력 항목은 옵션 항목이 될 수 없습니다");
        }
        if (hasLinkWithoutAllowedValues(links)) {
            throw new IllegalArgumentException("모든 값 허용이 아닌 항목에는 허용 값을 하나 이상 연결해야 합니다");
        }
        if (hasAllowedValuesOnAllValuesAllowed(links)) {
            throw new IllegalArgumentException("모든 값 허용 항목에는 허용 값을 연결하지 않습니다");
        }
        if (hasAllowedValueOfOtherAttribute(links)) {
            throw new IllegalArgumentException("허용 값은 그 항목의 목록 값이어야 합니다");
        }
        ProductType type = new ProductType();
        type.name = name;
        for (int i = 0; i < links.size(); i++) {
            LinkInfo link = links.get(i);
            type.attributes.add(ProductTypeAttribute.create(type, link, i + 1));
            for (AttributeValue value : link.allowedValues()) {
                type.allowedValues.add(ProductTypeAllowedValue.create(type, value));
            }
        }
        return type;
    }

    private static void requireNonNullValues(LinkInfo link) {
        requireNonNull(link, "link는 필수입니다");
        requireNonNull(link.attribute(), "attribute는 필수입니다");
        requireNonNull(link.role(), "role은 필수입니다");
        requireNonNull(link.allowedValues(), "allowedValues는 필수입니다");
        for (AttributeValue value : link.allowedValues()) {
            requireNonNull(value, "allowedValue는 필수입니다");
        }
    }

    private static boolean hasRequiredOption(List<LinkInfo> links) {
        return links.stream().anyMatch(link -> link.role() == AttributeRole.OPTION && link.required());
    }

    private static boolean hasManySizeRoles(List<LinkInfo> links) {
        return links.stream().filter(LinkInfo::sizeRole).count() > 1;
    }

    private static boolean hasSizeRoleOnInfo(List<LinkInfo> links) {
        return links.stream().anyMatch(link -> link.sizeRole() && link.role() != AttributeRole.OPTION);
    }

    private static boolean hasPriceAffectingOnInfo(List<LinkInfo> links) {
        return links.stream().anyMatch(link -> link.priceAffecting() && link.role() != AttributeRole.OPTION);
    }

    private static boolean hasTextOption(List<LinkInfo> links) {
        return links.stream().anyMatch(link ->
            link.role() == AttributeRole.OPTION && link.attribute().getInputType() == AttributeInputType.TEXT);
    }

    private static boolean hasLinkWithoutAllowedValues(List<LinkInfo> links) {
        return links.stream().anyMatch(link ->
            link.attribute().getInputType() != AttributeInputType.TEXT
                && !link.allValuesAllowed()
                && link.allowedValues().isEmpty());
    }

    private static boolean hasAllowedValuesOnAllValuesAllowed(List<LinkInfo> links) {
        return links.stream().anyMatch(link -> link.allValuesAllowed() && !link.allowedValues().isEmpty());
    }

    private static boolean hasAllowedValueOfOtherAttribute(List<LinkInfo> links) {
        return links.stream().anyMatch(link ->
            link.allowedValues().stream().anyMatch(value -> value.getAttribute() != link.attribute()));
    }

    public List<ProductTypeAttribute> getAttributes() {
        return Collections.unmodifiableList(attributes);
    }

    public List<ProductTypeAllowedValue> getAllowedValues() {
        return Collections.unmodifiableList(allowedValues);
    }
}
