package com.bbcc.kidly.boundedcontext.product.domain.product;

import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeValue;
import com.bbcc.kidly.boundedcontext.product.domain.type.Category;
import com.bbcc.kidly.boundedcontext.product.domain.type.CategoryMeasurementItem;
import com.bbcc.kidly.boundedcontext.product.domain.type.MeasurementItem;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductType;
import com.bbcc.kidly.global.error.BusinessException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 판매 시작 조건(SALE-03) 중 채우지 못한 항목을 문장 목록으로 계산한다. Product.startSale, validateSaleStartInputs, SaleInputsReplacement가 쓴다.
 */
final class SaleStartConditions {

    private SaleStartConditions() {
    }

    static void requireMet(List<ProductOption> options, List<SizeSpec> sizeSpecs, Category category,
        ProductType productType) {
        List<String> missing = missingItems(options, sizeSpecs, category, productType);
        if (!missing.isEmpty()) {
            throw new BusinessException(ProductErrorCode.SALE_START_CONDITION_NOT_MET,
                "판매를 시작할 수 없습니다. " + String.join(" ", missing));
        }
    }

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

    static void requireComplete(List<ProductOption> options, List<SizeSpec> sizeSpecs, Category category,
        ProductType productType, ProductErrorCode errorCode) {
        List<String> missing = missingItems(options, sizeSpecs, category, productType);
        if (!missing.isEmpty()) {
            throw new BusinessException(errorCode, String.join(" ", missing));
        }
    }

    private static void addSizeSpecMissing(List<String> missing, List<ProductOption> options,
        List<SizeSpec> sizeSpecs, Category category, ProductType productType) {
        Map<AttributeValue, SizeSpec> specBySize = new HashMap<>();
        for (SizeSpec spec : sizeSpecs) {
            specBySize.put(spec.getSizeValue(), spec);
        }
        for (AttributeValue sizeValue : ProductOptions.activeSizeValues(options, productType)) {
            SizeSpec spec = specBySize.get(sizeValue);
            // 실측 행이 없으면 FREE가 아닌 사이즈는 권장 키도 없으므로, 한 번에 고칠 수 있게 둘 다 알린다
            if (spec == null) {
                String lacking = ProductOptions.isFreeValue(sizeValue) ? "실측이" : "실측과 권장 키가";
                missing.add(sizeValue.getName() + " 사이즈에 " + lacking + " 없습니다.");
                continue;
            }
            addMeasurementMissing(missing, sizeValue, spec, category);
            // FREE가 아닌 사이즈는 권장 키도 있어야 하고, FREE는 권장 키가 선택이다 (SALE-03, SIZE-05).
            // 권장 키는 최솟값과 최댓값이 함께 있으므로 최솟값만 본다 (SIZE-03)
            if (spec.getMinHeightCm() == null && !ProductOptions.isFreeValue(sizeValue)) {
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

    // 마지막 글자에 받침이 있으면 "이", 없으면 "가"를 붙인다 (예: 총장이, 소매길이가). 마지막 글자가 한글이 아니면 "가"를 붙인다
    private static String subjectParticle(String word) {
        char last = word.charAt(word.length() - 1);
        if (last >= '가' && last <= '힣' && (last - '가') % 28 != 0) {
            return "이";
        }
        return "가";
    }
}
