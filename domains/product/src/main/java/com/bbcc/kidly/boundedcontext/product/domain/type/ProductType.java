package com.bbcc.kidly.boundedcontext.product.domain.type;

import com.bbcc.kidly.global.entity.BaseSoftDeleteEntity;
import com.bbcc.kidly.global.error.BusinessException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(schema = "product", name = "product_type")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductType extends BaseSoftDeleteEntity {

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

    @Column(nullable = false)
    private String name;

    @OneToMany(mappedBy = "productType", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<ProductTypeAttribute> attributes = new ArrayList<>();

    @OneToMany(mappedBy = "productType", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductTypeAllowedValue> allowedValues = new ArrayList<>();

    public static ProductType define(String name, List<LinkInfo> links) {
        var type = new ProductType();
        type.name = name;
        type.change(links, false);
        return type;
    }

    public void change(List<LinkInfo> links, boolean hasProducts) {
        ProductTypeLinkCheck.requireValid(links);
        ProductTypeLinkCheck.requireNoNewDeletedAllowedValue(links, allowedValues);
        if (hasProducts && ProductTypeChangeCheck.breaksProducts(this, links)) {
            throw new BusinessException(ProductTypeErrorCode.CHANGE_BREAKS_PRODUCTS);
        }
        var changedAttributes = new ArrayList<ProductTypeAttribute>();
        var changedAllowedValues = new ArrayList<ProductTypeAllowedValue>();
        for (int i = 0; i < links.size(); i++) {
            LinkInfo link = links.get(i);
            changedAttributes.add(changeOrCreateLink(link, i + 1));
            for (AttributeValue value : link.allowedValues()) {
                changedAllowedValues.add(findOrCreateAllowedValue(value));
            }
        }
        attributes.clear();
        attributes.addAll(changedAttributes);
        allowedValues.clear();
        allowedValues.addAll(changedAllowedValues);
    }

    private ProductTypeAttribute changeOrCreateLink(LinkInfo info, int sortOrder) {
        ProductTypeAttribute link = findLink(info.attribute()).orElse(null);
        if (link == null) {
            return ProductTypeAttribute.create(this, info, sortOrder);
        }
        link.change(info, sortOrder);
        return link;
    }

    private ProductTypeAllowedValue findOrCreateAllowedValue(AttributeValue value) {
        for (ProductTypeAllowedValue allowed : allowedValues) {
            if (allowed.getAttributeValue() == value) {
                return allowed;
            }
        }
        return ProductTypeAllowedValue.create(this, value);
    }

    public Optional<ProductTypeAttribute> findLink(Attribute attribute) {
        for (ProductTypeAttribute link : attributes) {
            if (link.getAttribute() == attribute) {
                return Optional.of(link);
            }
        }
        return Optional.empty();
    }

    public boolean hasSizeRole() {
        return attributes.stream().anyMatch(ProductTypeAttribute::isSizeRole);
    }

    public boolean isSizeValue(AttributeValue value) {
        return findLink(value.getAttribute()).filter(ProductTypeAttribute::isSizeRole).isPresent();
    }

    public boolean allows(AttributeValue value) {
        // 지운 값은 새 등록 · 수정에서 고를 수 없다 (TYPE-09)
        if (value.isDeleted()) {
            return false;
        }
        Optional<ProductTypeAttribute> link = findLink(value.getAttribute());
        if (link.isEmpty()) {
            return false;
        }
        if (link.get().isAllValuesAllowed()) {
            return true;
        }
        return allowedValues.stream().anyMatch(allowed -> allowed.getAttributeValue() == value);
    }

    public List<ProductTypeAttribute> inputAttributes() {
        var inputs = new ArrayList<ProductTypeAttribute>();
        for (AttributeRole role : List.of(AttributeRole.OPTION, AttributeRole.INFO)) {
            for (ProductTypeAttribute link : attributes) {
                if (link.getRole() == role) {
                    inputs.add(link);
                }
            }
        }
        return inputs;
    }

    public List<AttributeValue> selectableValues(List<AttributeValue> values) {
        var selectable = new ArrayList<AttributeValue>();
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

    // 지우지 않은 카테고리가 가리키는 유형(inUse)은 지울 수 없다(TYPE-10: 쓰이는 중인 기준 데이터는 지우지 않는다)
    public void delete(LocalDateTime now, boolean inUse) {
        if (inUse) {
            throw new BusinessException(ProductTypeErrorCode.PRODUCT_TYPE_IN_USE);
        }
        delete(now);
    }
}
