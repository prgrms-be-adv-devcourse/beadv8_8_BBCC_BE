package com.bbcc.kidly.boundedcontext.product.domain.product;

import com.bbcc.kidly.boundedcontext.product.domain.product.Product.PriceInfo;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeValue;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductType;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductTypeAttribute;
import com.bbcc.kidly.global.error.BusinessException;
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

    // 옵션의 가격 구분 묶음과 같은 값의 묶음을 가진 가격을 찾아 그 가격을 돌려준다. 없으면 거절한다 (PRICE-01)
    static long findPrice(List<AttributeValue> optionValues, List<PriceInfo> prices, ProductType productType) {
        Set<AttributeValue> group = priceGroup(optionValues, productType);
        for (PriceInfo price : prices) {
            if (new HashSet<>(price.values()).equals(group)) {
                return price.price();
            }
        }
        throw new BusinessException(ProductErrorCode.PRICE_GROUP_MISMATCH);
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
