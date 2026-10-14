package com.bbcc.kidly.boundedcontext.product.domain.product;

import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeValue;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 판매 시작 조건(SALE-03) 중 채우지 못한 항목을 문장 목록으로 계산한다. Product.startSale이 쓴다.
 */
final class SaleStartConditions {

    private SaleStartConditions() {
    }

    // 빠진 항목마다 마침표로 끝나는 문장을 하나씩 돌려준다. 모두 채웠으면 빈 목록이다
    static List<String> missingItems(List<ProductOption> options, List<SizeSpec> sizeSpecs, ProductType productType) {
        List<String> missing = new ArrayList<>();
        if (!ProductOptions.hasActiveOptions(options)) {
            missing.add("옵션이 하나 이상 있어야 합니다.");
        }
        if (productType.hasSizeRole()) {
            addSizeSpecMissing(missing, options, sizeSpecs, productType);
        }
        return missing;
    }

    // 옵션이 쓰는 사이즈 값마다 실측이 있어야 한다. 빠진 사이즈는 옵션에 처음 나온 순서대로 적는다
    private static void addSizeSpecMissing(List<String> missing, List<ProductOption> options,
        List<SizeSpec> sizeSpecs, ProductType productType) {
        Set<AttributeValue> specified = new HashSet<>();
        for (SizeSpec spec : sizeSpecs) {
            specified.add(spec.getSizeValue());
        }
        for (AttributeValue sizeValue : ProductOptions.activeSizeValues(options, productType)) {
            if (!specified.contains(sizeValue)) {
                missing.add(sizeValue.getName() + " 사이즈에 실측이 없습니다.");
            }
        }
    }
}
