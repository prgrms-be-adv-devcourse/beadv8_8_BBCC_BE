package com.bbcc.kidly.boundedcontext.recommend.domain.preference;

/**
 * 선호 점수에 넣는 신호의 종류다. 종류마다 강도, 세는 기간, 반영하는 친밀도가 다르다.
 * 구매확정은 방금 산 종류가 목록 위를 차지하지 않도록 브랜드 친밀도에만 넣는다.
 */
public enum PreferenceSignalType {
    PRODUCT_VIEWED(PreferenceWeights.VIEW_INTENSITY, PreferenceWeights.ACTIVITY_WINDOW_DAYS, true),
    CART_ITEM_ADDED(PreferenceWeights.CART_ADD_INTENSITY, PreferenceWeights.ACTIVITY_WINDOW_DAYS, true),
    SEARCH(PreferenceWeights.SEARCH_INTENSITY, PreferenceWeights.ACTIVITY_WINDOW_DAYS, true),
    PURCHASE_CONFIRMED(PreferenceWeights.PURCHASE_CONFIRMED_INTENSITY, PreferenceWeights.PURCHASE_WINDOW_DAYS, false);

    private final double intensity;

    private final int windowDays;

    private final boolean countsForCategory;

    PreferenceSignalType(double intensity, int windowDays, boolean countsForCategory) {
        this.intensity = intensity;
        this.windowDays = windowDays;
        this.countsForCategory = countsForCategory;
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
}
