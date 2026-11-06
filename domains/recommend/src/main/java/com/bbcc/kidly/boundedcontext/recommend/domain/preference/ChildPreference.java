package com.bbcc.kidly.boundedcontext.recommend.domain.preference;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 자녀 한 명의 선호 집계다. 카테고리별 활동 횟수 · 담기 횟수와 브랜드별 활동 횟수를 가진다.
 * 더하고 빼면 새 객체를 돌려준다.
 */
public final class ChildPreference {

    private static final int CATEGORY_POINTS = 3;

    private static final int CART_ADD_POINTS = 2;

    private static final int BRAND_POINTS = 2;

    private final Map<Long, Integer> categoryActivityCounts;

    private final Map<Long, Integer> categoryCartAddCounts;

    private final Map<String, Integer> brandActivityCounts;

    private ChildPreference(
            Map<Long, Integer> categoryActivityCounts,
            Map<Long, Integer> categoryCartAddCounts,
            Map<String, Integer> brandActivityCounts
    ) {
        this.categoryActivityCounts = categoryActivityCounts;
        this.categoryCartAddCounts = categoryCartAddCounts;
        this.brandActivityCounts = brandActivityCounts;
    }

    public static ChildPreference empty() {
        // Map.of()는 null 키를 찾으면 예외를 던지므로, 카테고리 · 브랜드가 없는 상품도 찾을 수 있게 HashMap으로 둔다
        return new ChildPreference(new HashMap<>(), new HashMap<>(), new HashMap<>());
    }

    /**
     * 저장된 카테고리 · 브랜드 줄로 집계를 만든다. 줄에서 값만 꺼내 담으므로 집계는 넘긴 목록과 이어지지 않는다.
     */
    public static ChildPreference of(List<ChildCategoryPreference> categories, List<ChildBrandPreference> brands) {
        Map<Long, Integer> activityCounts = new HashMap<>();
        Map<Long, Integer> cartAddCounts = new HashMap<>();
        for (ChildCategoryPreference category : categories) {
            activityCounts.put(category.getCategoryId(), category.getActivityCount());
            cartAddCounts.put(category.getCategoryId(), category.getCartAddCount());
        }
        return new ChildPreference(activityCounts, cartAddCounts, new HashMap<>());
    }

    public ChildPreference plus(PreferenceActivity activity) {
        return apply(activity, 1);
    }

    /**
     * 활동의 몫을 뺀다. 뺀 장바구니 담기, 기간이 지난 활동, 기준 시각 뒤의 활동을 집계에서 되돌릴 때 쓴다.
     */
    public ChildPreference minus(PreferenceActivity activity) {
        return apply(activity, -1);
    }

    public int categoryPoints(Long categoryId) {
        if (!appeared(categoryActivityCounts, categoryId)) {
            return 0;
        }
        if (appeared(categoryCartAddCounts, categoryId)) {
            return CATEGORY_POINTS + CART_ADD_POINTS;
        }
        return CATEGORY_POINTS;
    }

    public int brandPoints(String brand) {
        if (!PreferenceActivity.isBrandPresent(brand)) {
            return 0;
        }
        if (appeared(brandActivityCounts, brand)) {
            return BRAND_POINTS;
        }
        return 0;
    }

    // 횟수를 0에서 자르지 않는다. 자르면 빼기가 더하기보다 먼저 올 때 결과가 순서에 따라 달라진다
    private ChildPreference apply(PreferenceActivity activity, int delta) {
        Map<Long, Integer> activityCounts = new HashMap<>(categoryActivityCounts);
        Map<Long, Integer> cartAddCounts = new HashMap<>(categoryCartAddCounts);
        Map<String, Integer> brandCounts = new HashMap<>(brandActivityCounts);
        if (activity.hasCategory()) {
            activityCounts.merge(activity.categoryId(), delta, Integer::sum);
            if (activity.cartAdd()) {
                cartAddCounts.merge(activity.categoryId(), delta, Integer::sum);
            }
        }
        if (activity.hasBrand()) {
            brandCounts.merge(activity.brand(), delta, Integer::sum);
        }
        return new ChildPreference(activityCounts, cartAddCounts, brandCounts);
    }

    private static <K> boolean appeared(Map<K, Integer> counts, K key) {
        return counts.getOrDefault(key, 0) > 0;
    }
}
