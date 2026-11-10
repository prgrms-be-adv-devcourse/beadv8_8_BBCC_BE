package com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment;

import java.util.List;
import java.util.Set;

/**
 * 기준 옷의 구분이다. 구분마다 받는 실측 항목과 카테고리가 정해져 있고, 그 표를 이 enum이 들고 있다.
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

    boolean acceptsCode(String measurementCode) {
        return requiredCodes.contains(measurementCode) || optionalCodes.contains(measurementCode);
    }

    boolean hasAllRequired(Set<String> measurementCodes) {
        return measurementCodes.containsAll(requiredCodes);
    }

    /**
     * 카테고리 경로에 이 구분의 카테고리 이름이 하나라도 있으면 맞다.
     * 경로 전체를 보므로 상의를 티셔츠처럼 더 잘게 나눠도 같은 구분으로 판단한다.
     */
    boolean includesCategoryIn(List<String> categoryPathNames) {
        for (String name : categoryPathNames) {
            if (categoryNames.contains(name)) {
                return true;
            }
        }
        return false;
    }
}
