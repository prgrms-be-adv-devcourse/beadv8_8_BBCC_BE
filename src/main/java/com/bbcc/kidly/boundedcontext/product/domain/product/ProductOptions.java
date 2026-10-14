package com.bbcc.kidly.boundedcontext.product.domain.product;

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
