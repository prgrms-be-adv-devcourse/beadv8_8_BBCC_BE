package com.bbcc.kidly.boundedcontext.product.domain.product;

import static java.util.Objects.requireNonNull;

import com.bbcc.kidly.boundedcontext.product.domain.product.Product.SizeSpecInfo;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeValue;
import com.bbcc.kidly.boundedcontext.product.domain.type.Category;
import com.bbcc.kidly.boundedcontext.product.domain.type.CategoryMeasurementItem;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductType;
import com.bbcc.kidly.global.error.BusinessException;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class ProductSizeSpecs {

    private ProductSizeSpecs() {
    }

    static void requireNonNullValues(List<SizeSpecInfo> specs) {
        for (SizeSpecInfo spec : specs) {
            requireNonNull(spec.measurements(), "measurements는 필수입니다");
        }
    }

    static void validateNewSpecs(List<SizeSpecInfo> specs, List<SizeSpec> existing, List<ProductOption> options,
                                 Category category, ProductType productType) {
        validateSizeRole(productType);
        validateHeightRanges(specs);
        validateNoDuplicate(specs, existing);
        validateMeasurementItems(specs, category);
        validateSizeValuesInOptions(specs, options, productType);
    }

    // 사이즈 역할 항목이 없는 유형의 상품에는 실측과 권장 키가 없다 (SIZE-06)
    private static void validateSizeRole(ProductType productType) {
        if (!productType.hasSizeRole()) {
            throw new BusinessException(ProductErrorCode.SIZE_SPEC_NOT_ALLOWED);
        }
    }

    // 권장 키는 최솟값과 최댓값을 함께 넣어야 하며, 최솟값이 최댓값보다 크면 안 된다 (SIZE-03)
    private static void validateHeightRanges(List<SizeSpecInfo> specs) {
        for (SizeSpecInfo spec : specs) {
            Integer min = spec.minHeightCm();
            Integer max = spec.maxHeightCm();
            if ((min == null) != (max == null)) {
                throw new BusinessException(ProductErrorCode.INVALID_HEIGHT_RANGE);
            }
            if (min != null && min > max) {
                throw new BusinessException(ProductErrorCode.INVALID_HEIGHT_RANGE);
            }
        }
    }

    // 같은 사이즈 값의 실측은 요청 안에서도, 이미 있는 실측과도 겹치면 안 된다 (SIZE-03)
    private static void validateNoDuplicate(List<SizeSpecInfo> specs, List<SizeSpec> existing) {
        Set<AttributeValue> seen = new HashSet<>();
        for (SizeSpec spec : existing) {
            seen.add(spec.getSizeValue());
        }
        for (SizeSpecInfo spec : specs) {
            if (!seen.add(spec.sizeValue())) {
                throw new BusinessException(ProductErrorCode.DUPLICATE_SIZE_SPEC);
            }
        }
    }

    // 실측 값의 항목 코드는 모두 상품 카테고리의 실측 항목이어야 하고, 값은 0보다 커야 한다 (SIZE-04)
    private static void validateMeasurementItems(List<SizeSpecInfo> specs, Category category) {
        Set<String> allowed = new HashSet<>();
        for (CategoryMeasurementItem item : category.getMeasurementItems()) {
            allowed.add(item.getMeasurementItem().getCode());
        }
        for (SizeSpecInfo spec : specs) {
            if (!allowed.containsAll(spec.measurements().keySet())) {
                throw new BusinessException(ProductErrorCode.MEASUREMENT_ITEM_NOT_IN_CATEGORY);
            }
            for (BigDecimal value : spec.measurements().values()) {
                if (value.signum() <= 0) {
                    throw new IllegalArgumentException("실측 값은 0보다 커야 합니다");
                }
            }
        }
    }

    // 실측의 사이즈 값은 보관하지 않은 옵션이 쓰는 사이즈 값이어야 한다
    private static void validateSizeValuesInOptions(List<SizeSpecInfo> specs, List<ProductOption> options,
        ProductType productType) {
        Set<AttributeValue> used = ProductOptions.activeSizeValues(options, productType);
        for (SizeSpecInfo spec : specs) {
            if (!used.contains(spec.sizeValue())) {
                throw new BusinessException(ProductErrorCode.SIZE_NOT_IN_OPTIONS);
            }
        }
    }
}
