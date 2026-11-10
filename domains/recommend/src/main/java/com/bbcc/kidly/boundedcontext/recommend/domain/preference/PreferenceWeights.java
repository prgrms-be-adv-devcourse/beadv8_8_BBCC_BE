package com.bbcc.kidly.boundedcontext.recommend.domain.preference;

/**
 * 선호 점수 계산에 쓰는 숫자를 모아 둔다. 강도 · 기간 · 가중치를 바꿀 때는 이 파일만 고친다.
 */
public final class PreferenceWeights {

    public static final double VIEW_INTENSITY = 0.3;

    public static final double SEARCH_INTENSITY = 0.3;

    public static final double CART_ADD_INTENSITY = 0.6;

    public static final double PURCHASE_CONFIRMED_INTENSITY = 0.8;

    public static final int ACTIVITY_WINDOW_DAYS = 30;

    public static final int PURCHASE_WINDOW_DAYS = 180;

    private PreferenceWeights() {
    }
}
