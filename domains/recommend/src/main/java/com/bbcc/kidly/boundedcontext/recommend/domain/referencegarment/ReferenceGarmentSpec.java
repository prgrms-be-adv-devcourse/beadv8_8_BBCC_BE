package com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment;

import static java.util.Objects.requireNonNull;

import com.bbcc.kidly.global.error.BusinessException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * 보호자가 입력한 기준 옷 한 벌의 구분, 세부 종류, 실측이다. 규칙을 모두 통과한 값만 만들어진다.
 * record의 생성자는 public이라 검증을 건너뛰고 만들 수 있으므로, 생성자가 private인 클래스로 둔다.
 * 세부 종류가 구분에 맞는지는 카테고리 경로 이름으로 판단하므로 만들 때 경로를 함께 받고, 경로는 저장하지 않는다.
 * 문제가 여러 개면 세부 종류, 이 구분에 없는 실측 항목, 필수 실측, 값 순서로 먼저 걸린 하나만 알린다.
 */
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

    /**
     * @param categoryPath 세부 종류 카테고리의 경로다. 대분류부터 말단 순서이고, 찾지 못했으면 null이거나 비어 있다.
     *     마지막 칸의 ID가 categoryId와 달라도 찾지 못한 것으로 본다. 말단이 아닌 카테고리가 기준 옷이 되지 않게 하기 위해서다.
     */
    public static ReferenceGarmentSpec of(GarmentPart part, Long categoryId,
        List<GarmentCategoryPathEntry> categoryPath, Map<String, BigDecimal> measurements) {
        requireNonNull(part, "part는 필수입니다");
        requireNonNull(categoryId, "categoryId는 필수입니다");
        checkCategory(part, categoryId, categoryPath);
        Map<String, BigDecimal> given = measurements == null ? Map.of() : measurements;
        checkMeasurementCodes(part, given);
        checkMeasurementValues(given);
        return new ReferenceGarmentSpec(part, categoryId, Map.copyOf(given));
    }

    private static void checkCategory(GarmentPart part, Long categoryId, List<GarmentCategoryPathEntry> categoryPath) {
        if (categoryPath == null || categoryPath.isEmpty()
            || !categoryId.equals(categoryPath.get(categoryPath.size() - 1).categoryId())) {
            throw new BusinessException(ReferenceGarmentErrorCode.CATEGORY_NOT_FOUND);
        }
        if (!part.includesCategoryIn(namesOf(categoryPath))) {
            throw new BusinessException(ReferenceGarmentErrorCode.CATEGORY_PART_MISMATCH);
        }
    }

    private static List<String> namesOf(List<GarmentCategoryPathEntry> categoryPath) {
        List<String> names = new ArrayList<>();
        for (GarmentCategoryPathEntry entry : categoryPath) {
            names.add(entry.categoryName());
        }
        return names;
    }

    private static void checkMeasurementCodes(GarmentPart part, Map<String, BigDecimal> given) {
        for (String code : given.keySet()) {
            if (!part.acceptsCode(code)) {
                throw new BusinessException(ReferenceGarmentErrorCode.UNSUPPORTED_MEASUREMENT);
            }
        }
        if (!part.hasAllRequired(given.keySet())) {
            throw new BusinessException(ReferenceGarmentErrorCode.REQUIRED_MEASUREMENT_MISSING);
        }
    }

    private static void checkMeasurementValues(Map<String, BigDecimal> given) {
        for (BigDecimal value : given.values()) {
            if (isInvalidValue(value)) {
                throw new BusinessException(ReferenceGarmentErrorCode.INVALID_MEASUREMENT_VALUE);
            }
        }
    }

    private static boolean isInvalidValue(BigDecimal value) {
        return value == null
            || value.signum() <= 0
            || value.compareTo(MAX_CM) > 0
            || value.stripTrailingZeros().scale() > MAX_FRACTION_DIGITS;
    }
}
