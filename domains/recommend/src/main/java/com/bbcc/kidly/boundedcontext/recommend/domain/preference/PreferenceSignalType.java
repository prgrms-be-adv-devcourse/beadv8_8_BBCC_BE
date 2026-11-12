package com.bbcc.kidly.boundedcontext.recommend.domain.preference;

/**
 * 선호 점수에 넣는 신호의 종류다. 종류마다 강도, 세는 기간, 반영하는 친밀도가 다르다.
 * 구매확정은 방금 산 종류가 목록 위를 차지하지 않도록 브랜드 친밀도에만 넣는다.
 * 검색은 상품이 없으므로 취향 벡터에 넣지 않는다. 검색 결과 클릭은 누른 상품이 있어 상세 진입처럼 모두에 넣는다.
 */
public enum PreferenceSignalType {
    PRODUCT_VIEWED(PreferenceWeights.VIEW_INTENSITY, PreferenceWeights.ACTIVITY_WINDOW_DAYS, true, true),
    CART_ITEM_ADDED(PreferenceWeights.CART_ADD_INTENSITY, PreferenceWeights.ACTIVITY_WINDOW_DAYS, true, true),
    SEARCH(PreferenceWeights.SEARCH_INTENSITY, PreferenceWeights.ACTIVITY_WINDOW_DAYS, true, false),
    SEARCH_RESULT_CLICKED(
            PreferenceWeights.SEARCH_RESULT_CLICK_INTENSITY, PreferenceWeights.ACTIVITY_WINDOW_DAYS, true, true),
    PURCHASE_CONFIRMED(
            PreferenceWeights.PURCHASE_CONFIRMED_INTENSITY, PreferenceWeights.PURCHASE_WINDOW_DAYS, false, false);

    private final double intensity;

    private final int windowDays;

    private final boolean countsForCategory;

    private final boolean countsForTaste;

    PreferenceSignalType(double intensity, int windowDays, boolean countsForCategory, boolean countsForTaste) {
        this.intensity = intensity;
        this.windowDays = windowDays;
        this.countsForCategory = countsForCategory;
        this.countsForTaste = countsForTaste;
    }

    public double intensity() {
        return intensity;
    }

    public int windowDays() {
        return windowDays;
    }

    public boolean countsForCategory() {
        return countsForCategory;
    }

    public boolean countsForTaste() {
        return countsForTaste;
    }
}
