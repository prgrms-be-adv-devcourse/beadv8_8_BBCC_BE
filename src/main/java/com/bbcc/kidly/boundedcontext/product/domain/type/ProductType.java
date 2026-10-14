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
    @OrderBy("sortOrder ASC")
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
        if (hasNonSingleChoiceOption(links)) {
            throw new IllegalArgumentException("옵션 항목은 팔레트나 목록 항목만 될 수 있습니다");
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

    private static boolean hasNonSingleChoiceOption(List<LinkInfo> links) {
        return links.stream().anyMatch(link -> link.role() == AttributeRole.OPTION && !isSingleChoice(link));
    }

    private static boolean isSingleChoice(LinkInfo link) {
        AttributeInputType inputType = link.attribute().getInputType();
        return inputType == AttributeInputType.PALETTE || inputType == AttributeInputType.LIST;
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

    // 이 유형에서 그 항목의 연결을 찾는다. 유형에 연결되지 않은 항목이면 비어 있다
    public Optional<ProductTypeAttribute> findLink(Attribute attribute) {
        for (ProductTypeAttribute link : attributes) {
            if (link.getAttribute() == attribute) {
                return Optional.of(link);
            }
        }
        return Optional.empty();
    }

    // 이 유형에 사이즈 역할 항목이 있는지 본다
    public boolean hasSizeRole() {
        for (ProductTypeAttribute link : attributes) {
            if (link.isSizeRole()) {
                return true;
            }
        }
        return false;
    }

    // 값이 이 유형의 사이즈 역할 항목의 값인지 본다
    public boolean isSizeValue(AttributeValue value) {
        Optional<ProductTypeAttribute> link = findLink(value.getAttribute());
        return link.isPresent() && link.get().isSizeRole();
    }

    // 목록 값을 이 유형에서 고를 수 있는지 본다. 모든 값 허용 항목이면 그 항목의 값은 모두 고를 수 있고, 아니면 허용 값만 고를 수 있다
    public boolean allows(AttributeValue value) {
        Optional<ProductTypeAttribute> link = findLink(value.getAttribute());
        if (link.isEmpty()) {
            return false;
        }
        if (link.get().isAllValuesAllowed()) {
            return true;
        }
        for (ProductTypeAllowedValue allowed : allowedValues) {
            if (allowed.getAttributeValue() == value) {
                return true;
            }
        }
        return false;
    }

    // 등록 화면이 받는 입력 항목이다. 옵션 항목을 먼저, 정보 항목을 다음에 두고, 각 역할 안에서는 연결 순서를 따른다 (OPT-09)
    public List<ProductTypeAttribute> inputAttributes() {
        List<ProductTypeAttribute> inputs = new ArrayList<>();
        for (AttributeRole role : List.of(AttributeRole.OPTION, AttributeRole.INFO)) {
            for (ProductTypeAttribute link : attributes) {
                if (link.getRole() == role) {
                    inputs.add(link);
                }
            }
        }
        return inputs;
    }

    // 받은 목록 값 중 이 유형에서 고를 수 있는 값만 받은 순서대로 돌려준다 (TYPE-06)
    public List<AttributeValue> selectableValues(List<AttributeValue> values) {
        List<AttributeValue> selectable = new ArrayList<>();
        for (AttributeValue value : values) {
            if (allows(value)) {
                selectable.add(value);
            }
        }
        return selectable;
    }

    public List<ProductTypeAttribute> getAttributes() {
        return Collections.unmodifiableList(attributes);
    }

    public List<ProductTypeAllowedValue> getAllowedValues() {
        return Collections.unmodifiableList(allowedValues);
    }
}
