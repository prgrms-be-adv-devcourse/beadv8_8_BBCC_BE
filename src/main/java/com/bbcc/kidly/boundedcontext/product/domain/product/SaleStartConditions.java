package com.bbcc.kidly.boundedcontext.product.domain.product;

import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeValue;
import com.bbcc.kidly.boundedcontext.product.domain.type.Category;
import com.bbcc.kidly.boundedcontext.product.domain.type.CategoryMeasurementItem;
import com.bbcc.kidly.boundedcontext.product.domain.type.MeasurementItem;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 판매 시작 조건(SALE-03) 중 채우지 못한 항목을 문장 목록으로 계산한다. Product.startSale이 쓴다.
 */
final class SaleStartConditions {

    private SaleStartConditions() {
    }

    // 빠진 항목마다 마침표로 끝나는 문장을 하나씩 돌려준다. 모두 채웠으면 빈 목록이다
    static List<String> missingItems(List<ProductOption> options, List<SizeSpec> sizeSpecs, Category category,
        ProductType productType) {
        List<String> missing = new ArrayList<>();
        if (!ProductOptions.hasActiveOptions(options)) {
            missing.add("옵션이 하나 이상 있어야 합니다.");
        }
        if (productType.hasSizeRole()) {
            addSizeSpecMissing(missing, options, sizeSpecs, category, productType);
        }
        return missing;
    }

    // 옵션이 쓰는 사이즈 값마다 실측이 있어야 하고, 실측 값은 카테고리의 필수 실측 항목을 모두 채워야 한다.
    // 빠진 사이즈는 옵션에 처음 나온 순서대로 적는다
    private static void addSizeSpecMissing(List<String> missing, List<ProductOption> options,
        List<SizeSpec> sizeSpecs, Category category, ProductType productType) {
        Map<AttributeValue, SizeSpec> specBySize = new HashMap<>();
        for (SizeSpec spec : sizeSpecs) {
            specBySize.put(spec.getSizeValue(), spec);
        }
        for (AttributeValue sizeValue : ProductOptions.activeSizeValues(options, productType)) {
            SizeSpec spec = specBySize.get(sizeValue);
            if (spec == null) {
                missing.add(sizeValue.getName() + " 사이즈에 실측이 없습니다.");
                continue;
            }
            addMeasurementMissing(missing, sizeValue, spec, category);
            // 권장 키는 최솟값과 최댓값이 함께 있으므로 최솟값만 본다 (SIZE-03)
            if (spec.getMinHeightCm() == null) {
                missing.add(sizeValue.getName() + " 사이즈에 권장 키가 없습니다.");
            }
        }
    }

    // 실측에 빠진 필수 실측 항목을 카테고리의 실측 항목 순서대로 " · "로 이어 적는다 (SIZE-04)
    private static void addMeasurementMissing(List<String> missing, AttributeValue sizeValue, SizeSpec spec,
        Category category) {
        List<String> names = new ArrayList<>();
        for (CategoryMeasurementItem item : category.getMeasurementItems()) {
            MeasurementItem measurementItem = item.getMeasurementItem();
            if (!spec.getMeasurements().containsKey(measurementItem.getCode())) {
                names.add(measurementItem.getName());
            }
        }
        if (!names.isEmpty()) {
            String joined = String.join(" · ", names);
            missing.add(sizeValue.getName() + " 사이즈에 " + joined + subjectParticle(joined) + " 없습니다.");
        }
    }

    // 마지막 글자에 받침이 있으면 "이", 없으면 "가"를 붙인다 (예: 총장이, 소매길이가)
    private static String subjectParticle(String word) {
        char last = word.charAt(word.length() - 1);
        if (last >= '가' && last <= '힣' && (last - '가') % 28 != 0) {
            return "이";
        }
        return "가";
    }
}
