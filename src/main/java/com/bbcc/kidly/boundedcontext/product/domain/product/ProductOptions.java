package com.bbcc.kidly.boundedcontext.product.domain.product;

import com.bbcc.kidly.boundedcontext.product.domain.product.Product.OptionInfo;
import com.bbcc.kidly.boundedcontext.product.domain.product.Product.PriceInfo;
import com.bbcc.kidly.boundedcontext.product.domain.type.Attribute;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeRole;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeValue;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductType;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductTypeAttribute;
import com.bbcc.kidly.global.error.BusinessException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 옵션을 추가할 때 옵션 여러 개나 요청 전체를 함께 보는 검사를 모은다. Product.addOptions만 쓴다.
 */
final class ProductOptions {

    // 세미는 사이즈 무관을 값 글자 "FREE"로 판단한다 (SIZE-02)
    private static final String FREE_SIZE = "FREE";

    private ProductOptions() {
    }

    // 옵션 하나의 값을 검사한다. 값은 모두 유형의 옵션 항목 값이어야 하고, 유형의 필수 옵션 항목은 모두 값이 있어야 한다 (OPT-01)
    static void validateOption(List<AttributeValue> values, ProductType productType) {
        Set<Attribute> attributes = new HashSet<>();
        for (AttributeValue value : values) {
            Optional<ProductTypeAttribute> link = productType.findLink(value.getAttribute());
            if (link.isEmpty() || link.get().getRole() != AttributeRole.OPTION) {
                throw new BusinessException(ProductErrorCode.NOT_OPTION_ATTRIBUTE);
            }
            // 유형이 허용한 값만 받는다. 의류 사이즈는 Kidly 숫자 목록과 FREE만 허용한다 (OPT-04, SIZE-01)
            if (!productType.allows(value)) {
                throw new BusinessException(ProductErrorCode.OPTION_VALUE_NOT_ALLOWED);
            }
            // 옵션은 옵션 항목마다 값 하나다
            if (!attributes.add(value.getAttribute())) {
                throw new BusinessException(ProductErrorCode.MULTIPLE_VALUES_NOT_ALLOWED);
            }
        }
        for (ProductTypeAttribute link : productType.getAttributes()) {
            if (link.getRole() == AttributeRole.OPTION
                && link.isRequired()
                && !hasValueOf(values, link.getAttribute())) {
                throw new BusinessException(ProductErrorCode.REQUIRED_OPTION_MISSING);
            }
        }
    }

    private static boolean hasValueOf(List<AttributeValue> values, Attribute attribute) {
        for (AttributeValue value : values) {
            if (value.getAttribute() == attribute) {
                return true;
            }
        }
        return false;
    }

    // 한 상품의 옵션은 모두 같은 옵션 항목 묶음을 쓴다. 이미 있는 옵션(보관 제외)이 있으면 그 묶음에,
    // 없으면 요청의 첫 옵션 묶음에 맞춘다 (OPT-02)
    static void validateSameAttributes(List<OptionInfo> requested, List<ProductOption> existing) {
        Set<Attribute> expected = null;
        for (ProductOption option : existing) {
            if (option.getSaleStatus() != ProductOptionStatus.ARCHIVED) {
                expected = attributesOf(option);
                break;
            }
        }
        for (OptionInfo option : requested) {
            Set<Attribute> attributes = attributesOf(option.values());
            if (expected == null) {
                expected = attributes;
            }
            if (!attributes.equals(expected)) {
                throw new BusinessException(ProductErrorCode.OPTION_ATTRIBUTES_MISMATCH);
            }
        }
    }

    private static Set<Attribute> attributesOf(ProductOption option) {
        Set<Attribute> attributes = new HashSet<>();
        for (ProductOptionValue value : option.getValues()) {
            attributes.add(value.getAttribute());
        }
        return attributes;
    }

    private static Set<Attribute> attributesOf(List<AttributeValue> values) {
        Set<Attribute> attributes = new HashSet<>();
        for (AttributeValue value : values) {
            attributes.add(value.getAttribute());
        }
        return attributes;
    }

    // 한 상품에 FREE와 숫자 사이즈를 섞지 않는다. 이미 있는 옵션(보관 제외)과 요청의 사이즈 역할 값을 모두 본다 (SIZE-02)
    static void validateSizeFree(List<OptionInfo> requested, List<ProductOption> existing, ProductType productType) {
        List<AttributeValue> values = new ArrayList<>();
        for (ProductOption option : existing) {
            if (option.getSaleStatus() != ProductOptionStatus.ARCHIVED) {
                values.addAll(valuesOf(option));
            }
        }
        for (OptionInfo option : requested) {
            values.addAll(option.values());
        }
        boolean hasFree = false;
        boolean hasNumber = false;
        for (AttributeValue value : values) {
            if (!isSizeRole(value, productType)) {
                continue;
            }
            if (FREE_SIZE.equals(value.getName())) {
                hasFree = true;
            } else {
                hasNumber = true;
            }
        }
        if (hasFree && hasNumber) {
            throw new BusinessException(ProductErrorCode.SIZE_FREE_MIXED);
        }
    }

    // 보관하지 않은 옵션이 있고 그 옵션이 모두 FREE 사이즈이면 사이즈 무관이다. SIZE-02가 FREE와 숫자가 섞이지 않게 막으므로
    // 유형 정의 없이 값 글자로 판단한다 (SIZE-02)
    static boolean isSizeFree(List<ProductOption> options) {
        boolean hasOption = false;
        for (ProductOption option : options) {
            if (option.getSaleStatus() == ProductOptionStatus.ARCHIVED) {
                continue;
            }
            hasOption = true;
            if (!hasFreeSize(option)) {
                return false;
            }
        }
        return hasOption;
    }

    private static boolean hasFreeSize(ProductOption option) {
        for (ProductOptionValue value : option.getValues()) {
            if (FREE_SIZE.equals(value.getAttributeValue().getName())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSizeRole(AttributeValue value, ProductType productType) {
        Optional<ProductTypeAttribute> link = productType.findLink(value.getAttribute());
        return link.isPresent() && link.get().isSizeRole();
    }

    // 같은 값 묶음의 옵션은 하나만 둔다. 이미 있는 옵션(보관 제외)과 요청 안의 옵션을 모두 보고,
    // 값 묶음은 순서와 상관없는 집합으로 비교한다 (OPT-03)
    static void validateNoDuplicate(List<OptionInfo> requested, List<ProductOption> existing) {
        Set<Set<AttributeValue>> combinations = new HashSet<>();
        for (ProductOption option : existing) {
            if (option.getSaleStatus() != ProductOptionStatus.ARCHIVED) {
                combinations.add(valuesOf(option));
            }
        }
        for (OptionInfo option : requested) {
            if (!combinations.add(new HashSet<>(option.values()))) {
                throw new BusinessException(ProductErrorCode.DUPLICATE_OPTION);
            }
        }
    }

    private static Set<AttributeValue> valuesOf(ProductOption option) {
        Set<AttributeValue> values = new HashSet<>();
        for (ProductOptionValue value : option.getValues()) {
            values.add(value.getAttributeValue());
        }
        return values;
    }

    // 옵션의 가격 구분 묶음과 같은 값의 묶음을 가진 가격을 찾아 그 가격을 돌려준다.
    // 없으면 거절하고(PRICE-01), 같은 묶음의 가격이 둘 이상이어도 거절한다(PRICE-03)
    static long findPrice(List<AttributeValue> optionValues, List<PriceInfo> prices, ProductType productType) {
        Set<AttributeValue> group = priceGroup(optionValues, productType);
        List<PriceInfo> matched = new ArrayList<>();
        for (PriceInfo price : prices) {
            if (new HashSet<>(price.values()).equals(group)) {
                matched.add(price);
            }
        }
        if (matched.size() != 1) {
            throw new BusinessException(ProductErrorCode.PRICE_GROUP_MISMATCH);
        }
        return matched.getFirst().price();
    }

    // 옵션 값을 유형–항목 연결의 순서대로 늘어놓는다. 옵션 이름이 이 순서를 쓴다 (OPT-09)
    static List<AttributeValue> inTypeOrder(List<AttributeValue> values, ProductType productType) {
        List<AttributeValue> sorted = new ArrayList<>();
        for (ProductTypeAttribute link : productType.getAttributes()) {
            for (AttributeValue value : values) {
                if (value.getAttribute() == link.getAttribute()) {
                    sorted.add(value);
                }
            }
        }
        return sorted;
    }

    // 옵션의 값 중 값에 따라 가격이 달라지는 항목의 값을 모은다. 가격 구분 항목이 없는 유형은 빈 집합이다
    private static Set<AttributeValue> priceGroup(List<AttributeValue> optionValues, ProductType productType) {
        Set<AttributeValue> group = new HashSet<>();
        for (AttributeValue value : optionValues) {
            Optional<ProductTypeAttribute> link = productType.findLink(value.getAttribute());
            if (link.isPresent() && link.get().isPriceAffecting()) {
                group.add(value);
            }
        }
        return group;
    }
}
