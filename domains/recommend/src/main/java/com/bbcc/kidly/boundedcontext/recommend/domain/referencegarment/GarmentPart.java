package com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment;

import com.bbcc.kidly.global.error.BusinessException;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 기준 옷을 담는 칸이다. 같은 종류끼리 길이까지 비교할 수 있도록 옷 종류마다 칸을 둔다.
 * 실측 항목 코드는 상품의 사이즈별 실측과 같은 코드라서 나중에 cm로 바로 비교할 수 있다.
 * 선언 순서가 목록 순서다.
 */
public enum GarmentPart {

    TOP(Set.of("TOTAL_LENGTH", "CHEST_WIDTH"), Set.of("SHOULDER_WIDTH", "SLEEVE_LENGTH"), Set.of("상의")),
    // 상품 카테고리 「하의」를 「바지」로 바꾸는 중이라 바뀌기 전과 뒤의 이름을 모두 받는다
    BOTTOM(Set.of("TOTAL_LENGTH", "WAIST_WIDTH"), Set.of("RISE"), Set.of("하의", "바지")),
    OUTER(Set.of("TOTAL_LENGTH", "CHEST_WIDTH"), Set.of("SHOULDER_WIDTH", "SLEEVE_LENGTH"), Set.of("아우터")),
    DRESS(Set.of("TOTAL_LENGTH", "CHEST_WIDTH"), Set.of("SHOULDER_WIDTH"), Set.of("원피스")),
    SKIRT(Set.of("TOTAL_LENGTH", "WAIST_WIDTH"), Set.of("HIP_WIDTH"), Set.of("치마"));

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

    /**
     * 상품 카테고리 경로에 맞는 칸을 찾는다. 모자 · 양말처럼 어느 칸에도 맞지 않으면 null이다.
     */
    static GarmentPart of(List<String> categoryPathNames) {
        for (GarmentPart part : values()) {
            if (part.includesCategoryIn(categoryPathNames)) {
                return part;
            }
        }
        return null;
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

    /**
     * 이 칸의 기준 옷이 없을 때 대신 비교할 칸이다. 아우터와 원피스는 위에 입는 상의, 치마는 바지와 비슷해서 너비를 견줄 수 있다.
     * 상의와 바지는 대신할 칸이 없어 null이다.
     */
    GarmentPart substitute() {
        if (this == OUTER || this == DRESS) {
            return TOP;
        }
        if (this == SKIRT) {
            return BOTTOM;
        }
        return null;
    }
}
