package com.bbcc.kidly.boundedcontext.recommend.domain.preference;

/**
 * 활동 하나(상세 진입 · 장바구니 담기 · 검색)가 자녀 선호 집계에 주는 몫이다.
 * 카테고리 · 브랜드는 활동을 받은 때의 값이고, 없으면 null이다.
 */
public record PreferenceActivity(
        Long categoryId,
        String brand,
        boolean cartAdd
) {
}
