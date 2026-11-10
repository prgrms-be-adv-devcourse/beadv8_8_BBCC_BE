package com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment;

import com.bbcc.kidly.global.error.BusinessException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.experimental.Accessors;

@Getter
@Accessors(fluent = true)
public final class ReferenceGarmentSpec {

    // 아동복 실측은 가장 긴 바지 총장도 100cm 안팎이라, 200cm를 넘는 값은 mm를 cm로 잘못 넣은 실수로 본다.
    private static final BigDecimal MAX_CM = new BigDecimal("200");

    // 줄자의 mm 눈금과 맞춰 소수 첫째 자리까지만 받는다.
    private static final int MAX_FRACTION_DIGITS = 1;

    private final GarmentPart part;
    private final Long categoryId;
    private final Map<String, BigDecimal> measurements;

    private ReferenceGarmentSpec(GarmentPart part, Long categoryId, Map<String, BigDecimal> measurements) {
        this.part = part;
        this.categoryId = categoryId;
        this.measurements = measurements;
    }

    public static ReferenceGarmentSpec of(GarmentPart part, Long categoryId, List<String> categoryPathNames,
        Map<String, BigDecimal> measurements) {
        if (categoryPathNames == null || categoryPathNames.isEmpty()) {
            throw new BusinessException(ReferenceGarmentErrorCode.CATEGORY_NOT_FOUND);
        }
        if (!part.includesCategoryIn(categoryPathNames)) {
            throw new BusinessException(ReferenceGarmentErrorCode.CATEGORY_PART_MISMATCH);
        }
        Map<String, BigDecimal> given = measurements == null ? Map.of() : measurements;
        for (String code : given.keySet()) {
            if (!part.acceptsCode(code)) {
                throw new BusinessException(ReferenceGarmentErrorCode.UNSUPPORTED_MEASUREMENT);
            }
        }
        if (!part.hasAllRequired(given.keySet())) {
            throw new BusinessException(ReferenceGarmentErrorCode.REQUIRED_MEASUREMENT_MISSING);
        }
        for (BigDecimal value : given.values()) {
            if (isInvalidValue(value)) {
                throw new BusinessException(ReferenceGarmentErrorCode.INVALID_MEASUREMENT_VALUE);
            }
        }
        return new ReferenceGarmentSpec(part, categoryId, Map.copyOf(given));
    }

    private static boolean isInvalidValue(BigDecimal value) {
        return value == null
            || value.signum() <= 0
            || value.compareTo(MAX_CM) > 0
            || value.stripTrailingZeros().scale() > MAX_FRACTION_DIGITS;
    }
}
