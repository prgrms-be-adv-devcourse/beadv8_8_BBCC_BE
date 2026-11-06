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

    /**
     * 이 활동이 카테고리 줄을 고치는지 알려 준다.
     */
    public boolean hasCategory() {
        return categoryId != null;
    }

    /**
     * 이 활동이 브랜드 줄을 고치는지 알려 준다.
     */
    public boolean hasBrand() {
        return isBrandPresent(brand);
    }

    // 빈 문자열 브랜드끼리 일치해 점수를 받지 않도록 null · 빈 문자열 · 공백만 있는 브랜드는 브랜드가 없는 것과 같게 본다
    static boolean isBrandPresent(String brand) {
        return brand != null && !brand.isBlank();
    }
}
