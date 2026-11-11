package com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment;

import com.bbcc.kidly.global.error.BusinessException;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 실측 항목 코드는 상품의 사이즈별 실측과 같은 코드라서 나중에 cm로 바로 비교할 수 있다.
 * 카테고리 이름은 상의 · 아우터 · 원피스 상품은 상의 기준 옷과, 하의 · 치마 상품은 하의 기준 옷과 비교하는 대응을 따른다.
 */
public enum GarmentPart {

    TOP(Set.of("TOTAL_LENGTH", "CHEST_WIDTH"), Set.of("SHOULDER_WIDTH", "SLEEVE_LENGTH"),
        Set.of("상의", "아우터", "원피스")),
    BOTTOM(Set.of("TOTAL_LENGTH", "WAIST_WIDTH"), Set.of("RISE"), Set.of("하의", "치마"));

    private final Set<String> requiredCodes;
    private final Set<String> optionalCodes;
    private final Set<String> categoryNames;

    GarmentPart(Set<String> requiredCodes, Set<String> optionalCodes, Set<String> categoryNames) {
        this.requiredCodes = requiredCodes;
        this.optionalCodes = optionalCodes;
        this.categoryNames = categoryNames;
    }

    /**
     * 주소로 받은 구분 이름에서 구분을 찾는다. 응답의 구분과 같은 이름만 받으므로 소문자나 다른 말은 거절한다.
     */
    public static GarmentPart from(String name) {
        for (GarmentPart part : values()) {
            if (part.name().equals(name)) {
                return part;
            }
        }
        throw new BusinessException(ReferenceGarmentErrorCode.UNKNOWN_PART);
    }

    boolean acceptsCode(String measurementCode) {
        return requiredCodes.contains(measurementCode) || optionalCodes.contains(measurementCode);
    }

    boolean hasAllRequired(Set<String> measurementCodes) {
        return measurementCodes.containsAll(requiredCodes);
    }

    /**
     * 경로 전체를 보므로 상의를 티셔츠처럼 더 잘게 나눠도 같은 구분으로 판단한다.
     */
    boolean includesCategoryIn(List<String> categoryPathNames) {
        return !Collections.disjoint(categoryNames, categoryPathNames);
    }
}
