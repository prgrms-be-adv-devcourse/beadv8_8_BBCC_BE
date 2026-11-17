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

    private static List<String> missingItems(List<ProductOption> options, List<SizeSpec> sizeSpecs, Category category,
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
        ProductType productType) {
        List<String> missing = missingItems(options, sizeSpecs, category, productType);
        if (!missing.isEmpty()) {
            throw new BusinessException(ProductErrorCode.SALE_START_INPUT_MISSING, String.join(" ", missing));
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
            if (spec == null) {
                String lacking = ProductOptions.isFreeValue(sizeValue) ? "실측이" : "실측과 권장 키가";
                missing.add(sizeValue.getName() + " 사이즈에 " + lacking + " 없습니다.");
                continue;
            }
            addMeasurementMissing(missing, sizeValue, spec, category);
            if (spec.getMinHeightCm() == null && !ProductOptions.isFreeValue(sizeValue)) {
                missing.add(sizeValue.getName() + " 사이즈에 권장 키가 없습니다.");
            }
        }
    }

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
