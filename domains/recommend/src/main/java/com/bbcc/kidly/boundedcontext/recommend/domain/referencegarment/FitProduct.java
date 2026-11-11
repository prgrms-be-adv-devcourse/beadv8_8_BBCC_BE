package com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 기준 옷과 핏을 비교할 상품 쪽 입력이다. 상품의 카테고리와 사이즈별 실측(cm)만 담는다.
 */
public record FitProduct(Long categoryId, List<String> categoryPathNames, boolean sizeFree,
                         List<SizeMeasurements> sizes) {

    /**
     * 사이즈 하나의 실측이다. 키는 실측 항목 코드, 값은 cm다.
     */
    public record SizeMeasurements(String size, Map<String, BigDecimal> measurements) {
    }
}
