package com.bbcc.kidly.boundedcontext.recommend.domain.preference;

/**
 * 선호 점수 계산에 쓰는 숫자를 모아 둔다. 강도 · 기간 · 가중치를 바꿀 때는 이 파일만 고친다.
 * 상품 점수의 가중치는 취향 벡터가 있는 자녀의 식(TASTE_)과 없는 자녀의 식(RULE_)으로 나뉘고, 식마다 합이 1이다.
 */
public final class PreferenceWeights {

    public static final double VIEW_INTENSITY = 0.3;

    public static final double SEARCH_INTENSITY = 0.3;

    public static final double CART_ADD_INTENSITY = 0.6;

    public static final double PURCHASE_CONFIRMED_INTENSITY = 0.8;

    public static final int ACTIVITY_WINDOW_DAYS = 30;

    public static final int PURCHASE_WINDOW_DAYS = 180;

    /**
     * 자녀를 고르지 않은 회원 활동을 자녀 점수에 넣을 때의 배율이다.
     */
    public static final double MEMBER_SHARE = 0.5;

    public static final int FRESHNESS_DAYS = 7;

    public static final double TASTE_SIMILARITY_WEIGHT = 0.45;

    public static final double TASTE_CATEGORY_WEIGHT = 0.15;

    public static final double TASTE_BRAND_WEIGHT = 0.25;

    public static final double TASTE_FRESHNESS_WEIGHT = 0.15;

    public static final double RULE_CATEGORY_WEIGHT = 0.45;

    public static final double RULE_BRAND_WEIGHT = 0.35;

    public static final double RULE_FRESHNESS_WEIGHT = 0.20;

    /**
     * 상품 점수를 반올림하는 소수 자리 수다. 아주 작은 계산 차이로 요청마다 순서가 바뀌지 않게 한다.
     */
    public static final int SCORE_SCALE = 4;

    private PreferenceWeights() {
    }
}
