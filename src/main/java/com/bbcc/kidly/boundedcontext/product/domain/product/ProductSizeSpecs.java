package com.bbcc.kidly.boundedcontext.product.domain.product;

import static java.util.Objects.requireNonNull;

import com.bbcc.kidly.boundedcontext.product.domain.product.Product.AddSizeSpecsInfo;
import com.bbcc.kidly.boundedcontext.product.domain.product.Product.SizeSpecInfo;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeValue;
import com.bbcc.kidly.boundedcontext.product.domain.type.Category;
import com.bbcc.kidly.boundedcontext.product.domain.type.CategoryMeasurementItem;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductType;
import com.bbcc.kidly.global.error.BusinessException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 실측을 추가할 때 실측 여러 개나 요청 전체를 함께 보는 검사를 모은다. Product.addSizeSpecs만 쓴다.
 */
final class ProductSizeSpecs {

    private ProductSizeSpecs() {
    }

    // 실측 목록, 그 안의 실측과 사이즈 값, 실측 값 묶음과 그 안의 값은 null이면 안 된다. 권장 키는 null을 허용한다
    static void requireNonNullValues(AddSizeSpecsInfo info) {
        requireNonNull(info.specs(), "specs는 필수입니다");
        for (SizeSpecInfo spec : info.specs()) {
            requireNonNull(spec, "spec은 필수입니다");
            requireNonNull(spec.sizeValue(), "sizeValue는 필수입니다");
            requireNonNull(spec.measurements(), "measurements는 필수입니다");
            for (BigDecimal value : spec.measurements().values()) {
                requireNonNull(value, "실측 값은 필수입니다");
            }
        }
    }

    // 새 실측을 다음 순서로 검사한다: 사이즈 역할, 권장 키 범위, 중복, 실측 항목, 사이즈 값이 옵션에 있는지
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

    // 권장 키는 0보다 크고, 최솟값과 최댓값을 함께 넣어야 하며, 최솟값이 최댓값보다 크면 안 된다 (SIZE-03)
    private static void validateHeightRanges(List<SizeSpecInfo> specs) {
        for (SizeSpecInfo spec : specs) {
            Integer min = spec.minHeightCm();
            Integer max = spec.maxHeightCm();
            // 0 이하는 요청 DTO가 먼저 막으므로 여기까지 오면 코드가 잘못 부른 것이다. 최솟값만 보면 되는 까닭은,
            // 최솟값이 0보다 크고 최댓값이 최솟값보다 작지 않으면 최댓값도 0보다 크기 때문이다
            if (min != null && min <= 0) {
                throw new IllegalArgumentException("권장 키는 0보다 커야 합니다");
            }
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

    // 판매대기 상품을 이어 쓸 때 받은 실측이 지금 실측과 다른지 본다. 사이즈 값이 짝지어지지 않으면 다른 것으로 본다
    static boolean differs(List<SizeSpec> current, List<SizeSpecInfo> requested) {
        if (current.size() != requested.size()) {
            return true;
        }
        for (SizeSpecInfo spec : requested) {
            SizeSpec matched = findBySize(current, spec.sizeValue());
            if (matched == null
                || !Objects.equals(matched.getMinHeightCm(), spec.minHeightCm())
                || !Objects.equals(matched.getMaxHeightCm(), spec.maxHeightCm())
                || !sameMeasurements(matched.getMeasurements(), spec.measurements())) {
                return true;
            }
        }
        return false;
    }

    // 받은 실측으로 바꿔 넣은 뒤의 실측을 검사용으로 만들어 돌려준다. 상품의 실측 목록에는 붙이지 않는다
    static List<SizeSpec> preview(Product product, List<SizeSpecInfo> requested) {
        List<SizeSpec> previews = new ArrayList<>();
        for (SizeSpecInfo spec : requested) {
            previews.add(SizeSpec.create(product, spec));
        }
        return previews;
    }

    // 사이즈 값이 같은 실측은 객체를 그대로 두고 권장 키와 실측 값만 받은 값으로 맞추고, 받지 않은 사이즈의 실측은 뺀다
    static void rewrite(Product product, List<SizeSpec> current, List<SizeSpecInfo> requested) {
        List<SizeSpec> notRequested = new ArrayList<>();
        for (SizeSpec sizeSpec : current) {
            SizeSpecInfo matched = findRequested(requested, sizeSpec.getSizeValue());
            if (matched == null) {
                notRequested.add(sizeSpec);
                continue;
            }
            sizeSpec.revise(matched.minHeightCm(), matched.maxHeightCm(), matched.measurements());
        }
        current.removeAll(notRequested);
        // 지금 없는 사이즈의 실측은 등록할 때와 같은 방식으로 새로 만든다
        for (SizeSpecInfo spec : requested) {
            if (findBySize(current, spec.sizeValue()) == null) {
                current.add(SizeSpec.create(product, spec));
            }
        }
    }

    // 사이즈 값은 같은 영속성 컨텍스트의 객체라 ==로 비교한다. 없으면 null이다
    private static SizeSpec findBySize(List<SizeSpec> current, AttributeValue sizeValue) {
        for (SizeSpec sizeSpec : current) {
            if (sizeSpec.getSizeValue() == sizeValue) {
                return sizeSpec;
            }
        }
        return null;
    }

    private static SizeSpecInfo findRequested(List<SizeSpecInfo> requested, AttributeValue sizeValue) {
        for (SizeSpecInfo spec : requested) {
            if (spec.sizeValue() == sizeValue) {
                return spec;
            }
        }
        return null;
    }

    // 실측 값은 크기로 비교한다. 42와 42.0은 같은 길이라 같은 값으로 본다. 같은 요청을 다시 보냈을 때
    // 자릿수만 다른 값 때문에 바뀐 것으로 보지 않게 하기 위해서다
    private static boolean sameMeasurements(Map<String, BigDecimal> current, Map<String, BigDecimal> requested) {
        if (!current.keySet().equals(requested.keySet())) {
            return false;
        }
        for (Map.Entry<String, BigDecimal> entry : requested.entrySet()) {
            if (current.get(entry.getKey()).compareTo(entry.getValue()) != 0) {
                return false;
            }
        }
        return true;
    }
}
