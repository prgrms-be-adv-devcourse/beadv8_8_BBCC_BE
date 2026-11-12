package com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment;

import static java.util.Objects.requireNonNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 기준 옷과 핏을 비교할 상품 쪽 입력이다. 상품의 카테고리, 사이즈 무관 여부, 사이즈별 실측(cm)을 담는다.
 *
 * @param categoryId 상품의 말단 카테고리 ID다. 카테고리 칸이 없던 예전 상품 복사본은 null일 수 있다
 * @param categoryPathNames 대분류부터 말단까지의 카테고리 이름이다
 */
public record FitProduct(Long categoryId, List<String> categoryPathNames, boolean sizeFree,
                         List<SizeMeasurements> sizes) {

    public FitProduct {
        categoryPathNames = List.copyOf(requireNonNull(categoryPathNames, "categoryPathNames는 필수입니다"));
        sizes = List.copyOf(requireNonNull(sizes, "sizes는 필수입니다"));
    }

    /**
     * 사이즈 하나의 실측이다. 키는 실측 항목 코드, 값은 cm다.
     */
    public record SizeMeasurements(String size, Map<String, BigDecimal> measurements) {

        public SizeMeasurements {
            requireNonNull(size, "size는 필수입니다");
            measurements = Map.copyOf(requireNonNull(measurements, "measurements는 필수입니다"));
        }
    }
}
