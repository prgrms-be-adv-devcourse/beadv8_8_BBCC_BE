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
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class ProductOptions {

    private static final String FREE_SIZE = "FREE";

    private ProductOptions() {
    }

    static boolean isFreeValue(AttributeValue value) {
        return FREE_SIZE.equals(value.getName());
    }

    static void validateNewOptions(List<OptionInfo> requested, List<PriceInfo> prices, List<ProductOption> existing,
        ProductType productType) {
        for (OptionInfo option : requested) {
            validateOption(option.values(), productType);
        }
        List<List<AttributeValue>> valueLists = valueLists(requested, existing);
        validateSameAttributes(valueLists);
        validateNoDuplicate(valueLists);
        validateSizeFree(valueLists, productType);
        validateExistingPrices(prices, existing, productType);
    }

    // 옵션 하나의 값을 검사한다. 값은 모두 유형의 옵션 항목 값이어야 하고, 유형의 필수 옵션 항목은 모두 값이 있어야 한다 (OPT-01)
    private static void validateOption(List<AttributeValue> values, ProductType productType) {
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
                && !attributes.contains(link.getAttribute())) {
                throw new BusinessException(ProductErrorCode.REQUIRED_OPTION_MISSING);
            }
        }
    }

    private static List<List<AttributeValue>> valueLists(List<OptionInfo> requested, List<ProductOption> existing) {
        List<List<AttributeValue>> lists = activeValueLists(existing);
        for (OptionInfo option : requested) {
            lists.add(option.values());
        }
        return lists;
    }

    // 한 상품의 옵션은 모두 같은 옵션 항목 묶음을 쓴다. 이미 있는 옵션(보관 제외)이 있으면 그 묶음에,
    // 없으면 요청의 첫 옵션 묶음에 맞춘다 (OPT-02)
    private static void validateSameAttributes(List<List<AttributeValue>> valueLists) {
        Set<Attribute> expected = attributesOf(valueLists.getFirst());
        for (List<AttributeValue> values : valueLists) {
            if (!attributesOf(values).equals(expected)) {
                throw new BusinessException(ProductErrorCode.OPTION_ATTRIBUTES_MISMATCH);
            }
        }
    }

    private static Set<Attribute> attributesOf(List<AttributeValue> values) {
        Set<Attribute> attributes = new HashSet<>();
        for (AttributeValue value : values) {
            attributes.add(value.getAttribute());
        }
        return attributes;
    }

    // 한 상품에 FREE와 숫자 사이즈를 섞지 않는다. 이미 있는 옵션(보관 제외)과 요청의 사이즈 역할 값을 모두 본다 (SIZE-02)
    private static void validateSizeFree(List<List<AttributeValue>> valueLists, ProductType productType) {
        boolean hasFree = false;
        boolean hasNumber = false;
        for (List<AttributeValue> values : valueLists) {
            for (AttributeValue value : values) {
                if (!productType.isSizeValue(value)) {
                    continue;
                }
                if (isFreeValue(value)) {
                    hasFree = true;
                } else {
                    hasNumber = true;
                }
            }
        }
        if (hasFree && hasNumber) {
            throw new BusinessException(ProductErrorCode.SIZE_FREE_MIXED);
        }
    }

    static boolean isSizeFree(List<ProductOption> options) {
        List<List<AttributeValue>> lists = activeValueLists(options);
        for (List<AttributeValue> values : lists) {
            if (values.stream().noneMatch(ProductOptions::isFreeValue)) {
                return false;
            }
        }
        return !lists.isEmpty();
    }

    static Set<AttributeValue> activeSizeValues(List<ProductOption> options, ProductType productType) {
        Set<AttributeValue> sizeValues = new LinkedHashSet<>();
        for (List<AttributeValue> values : activeValueLists(options)) {
            for (AttributeValue value : values) {
                if (productType.isSizeValue(value)) {
                    sizeValues.add(value);
                }
            }
        }
        return sizeValues;
    }

    static boolean hasActiveOptions(List<ProductOption> options) {
        return !activeOptions(options).isEmpty();
    }

    // 같은 값 묶음의 옵션은 하나만 둔다. 이미 있는 옵션(보관 제외)과 요청 안의 옵션을 모두 보고,
    // 값 묶음은 순서와 상관없는 집합으로 비교한다 (OPT-03)
    private static void validateNoDuplicate(List<List<AttributeValue>> valueLists) {
        Set<Set<AttributeValue>> seen = new HashSet<>();
        for (List<AttributeValue> values : valueLists) {
            if (!seen.add(new HashSet<>(values))) {
                throw new BusinessException(ProductErrorCode.DUPLICATE_OPTION);
            }
        }
    }

    private static List<List<AttributeValue>> activeValueLists(List<ProductOption> existing) {
        List<List<AttributeValue>> lists = new ArrayList<>();
        for (ProductOption option : activeOptions(existing)) {
            lists.add(valuesOf(option));
        }
        return lists;
    }

    static List<ProductOption> activeOptions(List<ProductOption> existing) {
        List<ProductOption> active = new ArrayList<>();
        for (ProductOption option : existing) {
            if (option.getSaleStatus() != ProductOptionStatus.ARCHIVED) {
                active.add(option);
            }
        }
        return active;
    }

    static List<AttributeValue> valuesOf(ProductOption option) {
        List<AttributeValue> values = new ArrayList<>();
        for (ProductOptionValue value : option.getValues()) {
            values.add(value.getAttributeValue());
        }
        return values;
    }

    // 이미 있는 옵션(보관 제외)과 같은 가격 구분 묶음의 가격은 그 옵션의 가격과 같아야 한다 (PRICE-03)
    private static void validateExistingPrices(
        List<PriceInfo> prices, List<ProductOption> existing, ProductType productType) {
        for (ProductOption option : activeOptions(existing)) {
            Set<AttributeValue> group = priceGroup(valuesOf(option), productType);
            for (PriceInfo price : prices) {
                if (new HashSet<>(price.values()).equals(group) && price.price() != option.getPrice()) {
                    throw new BusinessException(ProductErrorCode.EXISTING_OPTION_PRICE_MISMATCH);
                }
            }
        }
    }

    // 옵션의 가격 구분 묶음과 같은 값의 묶음을 가진 가격을 찾아 그 가격을 돌려준다.
    // 없으면 거절하고(PRICE-01), 같은 묶음의 가격이 둘 이상이어도 거절한다(PRICE-03)
    private static long findPrice(List<AttributeValue> optionValues, List<PriceInfo> prices, ProductType productType) {
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

    static ProductOption newOption(
        Product product, OptionInfo option, List<PriceInfo> prices, ProductType productType) {
        long price = findPrice(option.values(), prices, productType);
        return ProductOption.create(product, price, option.skuCode(), inTypeOrder(option.values(), productType));
    }

    private static List<AttributeValue> inTypeOrder(List<AttributeValue> values, ProductType productType) {
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

    static List<PriceInfo> currentPrices(List<ProductOption> options, ProductType productType) {
        Map<Set<AttributeValue>, PriceInfo> groups = new LinkedHashMap<>();
        for (ProductOption option : activeOptions(options)) {
            Set<AttributeValue> group = priceGroup(valuesOf(option), productType);
            groups.putIfAbsent(group, new PriceInfo(new ArrayList<>(group), option.getPrice()));
        }
        return new ArrayList<>(groups.values());
    }

    private static Set<AttributeValue> priceGroup(Collection<AttributeValue> optionValues, ProductType productType) {
        Set<AttributeValue> group = new LinkedHashSet<>();
        for (AttributeValue value : optionValues) {
            if (productType.findLink(value.getAttribute()).filter(ProductTypeAttribute::isPriceAffecting).isPresent()) {
                group.add(value);
            }
        }
        return group;
    }
}
