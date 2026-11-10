package com.bbcc.kidly.boundedcontext.recommend.domain.preference;

/**
 * 선호 점수에 넣는 신호의 종류다. 종류마다 강도와 세는 기간이 다르다.
 */
public enum PreferenceSignalType {
    PRODUCT_VIEWED(PreferenceWeights.VIEW_INTENSITY, PreferenceWeights.ACTIVITY_WINDOW_DAYS),
    CART_ITEM_ADDED(PreferenceWeights.CART_ADD_INTENSITY, PreferenceWeights.ACTIVITY_WINDOW_DAYS),
    SEARCH(PreferenceWeights.SEARCH_INTENSITY, PreferenceWeights.ACTIVITY_WINDOW_DAYS);

    private final double intensity;

    private final int windowDays;

    PreferenceSignalType(double intensity, int windowDays) {
        this.intensity = intensity;
        this.windowDays = windowDays;
    }

    public double intensity() {
        return intensity;
    }

    public int windowDays() {
        return windowDays;
    }
}
