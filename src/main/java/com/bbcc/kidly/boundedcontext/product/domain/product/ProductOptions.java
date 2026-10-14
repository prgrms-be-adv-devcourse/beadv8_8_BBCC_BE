package com.bbcc.kidly.boundedcontext.product.domain.product;

import static java.util.Objects.requireNonNull;

import com.bbcc.kidly.boundedcontext.product.domain.product.Product.AddOptionsInfo;
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
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 옵션을 추가할 때 옵션 여러 개나 요청 전체를 함께 보는 검사를 모은다. Product.addOptions와 ProductSizeSpecs가 쓴다.
 */
final class ProductOptions {

    // 세미는 사이즈 무관을 값 글자 "FREE"로 판단한다 (SIZE-02)
    private static final String FREE_SIZE = "FREE";

    private ProductOptions() {
    }

    // 옵션 목록과 가격 목록, 그 안의 값은 null이면 안 된다. SKU 코드는 null을 허용한다
    static void requireNonNullValues(AddOptionsInfo info) {
        requireNonNull(info.options(), "options는 필수입니다");
        requireNonNull(info.prices(), "prices는 필수입니다");
        for (OptionInfo option : info.options()) {
            requireNonNull(option, "option은 필수입니다");
            requireNonNull(option.values(), "values는 필수입니다");
            option.values().forEach(value -> requireNonNull(value, "value는 필수입니다"));
        }
        for (PriceInfo price : info.prices()) {
            requireNonNull(price, "price는 필수입니다");
            requireNonNull(price.values(), "values는 필수입니다");
            requireNonNull(price.price(), "판매가는 필수입니다");
            price.values().forEach(value -> requireNonNull(value, "value는 필수입니다"));
        }
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
                && !attributes.contains(link.getAttribute())) {
                throw new BusinessException(ProductErrorCode.REQUIRED_OPTION_MISSING);
            }
        }
    }

    // 이미 있는 옵션(보관 제외)의 값 목록 뒤에 요청 옵션의 값 목록을 붙인다. OPT-02 · 03과 SIZE-02 검사가 함께 쓴다
    static List<List<AttributeValue>> valueLists(List<OptionInfo> requested, List<ProductOption> existing) {
        List<List<AttributeValue>> lists = activeValueLists(existing);
        for (OptionInfo option : requested) {
            lists.add(option.values());
        }
        return lists;
    }

    // 한 상품의 옵션은 모두 같은 옵션 항목 묶음을 쓴다. 이미 있는 옵션(보관 제외)이 있으면 그 묶음에,
    // 없으면 요청의 첫 옵션 묶음에 맞춘다 (OPT-02)
    static void validateSameAttributes(List<List<AttributeValue>> valueLists) {
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
    static void validateSizeFree(List<List<AttributeValue>> valueLists, ProductType productType) {
        boolean hasFree = false;
        boolean hasNumber = false;
        for (List<AttributeValue> values : valueLists) {
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
        }
        if (hasFree && hasNumber) {
            throw new BusinessException(ProductErrorCode.SIZE_FREE_MIXED);
        }
    }

    // 보관하지 않은 옵션이 있고 그 옵션이 모두 FREE 사이즈이면 사이즈 무관이다. SIZE-02가 FREE와 숫자가 섞이지 않게 막으므로
    // 유형 정의 없이 값 글자로 판단한다 (SIZE-02)
    static boolean isSizeFree(List<ProductOption> options) {
        List<List<AttributeValue>> lists = activeValueLists(options);
        for (List<AttributeValue> values : lists) {
            boolean hasFree = false;
            for (AttributeValue value : values) {
                hasFree = hasFree || FREE_SIZE.equals(value.getName());
            }
            if (!hasFree) {
                return false;
            }
        }
        return !lists.isEmpty();
    }

    static boolean isSizeRole(AttributeValue value, ProductType productType) {
        return productType.findLink(value.getAttribute()).filter(ProductTypeAttribute::isSizeRole).isPresent();
    }

    // 같은 값 묶음의 옵션은 하나만 둔다. 이미 있는 옵션(보관 제외)과 요청 안의 옵션을 모두 보고,
    // 값 묶음은 순서와 상관없는 집합으로 비교한다 (OPT-03)
    static void validateNoDuplicate(List<List<AttributeValue>> valueLists) {
        Set<Set<AttributeValue>> seen = new HashSet<>();
        for (List<AttributeValue> values : valueLists) {
            if (!seen.add(new HashSet<>(values))) {
                throw new BusinessException(ProductErrorCode.DUPLICATE_OPTION);
            }
        }
    }

    // 이미 있는 옵션(보관 제외)의 값 목록이다
    static List<List<AttributeValue>> activeValueLists(List<ProductOption> existing) {
        List<List<AttributeValue>> lists = new ArrayList<>();
        for (ProductOption option : activeOptions(existing)) {
            lists.add(valuesOf(option));
        }
        return lists;
    }

    // 보관한 옵션은 판매 대상이 아니라서 모든 검사에서 뺀다
    private static List<ProductOption> activeOptions(List<ProductOption> existing) {
        List<ProductOption> active = new ArrayList<>();
        for (ProductOption option : existing) {
            if (option.getSaleStatus() != ProductOptionStatus.ARCHIVED) {
                active.add(option);
            }
        }
        return active;
    }

    private static List<AttributeValue> valuesOf(ProductOption option) {
        List<AttributeValue> values = new ArrayList<>();
        for (ProductOptionValue value : option.getValues()) {
            values.add(value.getAttributeValue());
        }
        return values;
    }

    // 이미 있는 옵션(보관 제외)과 같은 가격 구분 묶음의 가격은 그 옵션의 가격과 같아야 한다 (PRICE-03)
    static void validateExistingPrices(List<PriceInfo> prices, List<ProductOption> existing, ProductType productType) {
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
    private static Set<AttributeValue> priceGroup(Collection<AttributeValue> optionValues, ProductType productType) {
        Set<AttributeValue> group = new HashSet<>();
        for (AttributeValue value : optionValues) {
            if (productType.findLink(value.getAttribute()).filter(ProductTypeAttribute::isPriceAffecting).isPresent()) {
                group.add(value);
            }
        }
        return group;
    }
}
