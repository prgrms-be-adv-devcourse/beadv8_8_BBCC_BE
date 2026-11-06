package com.bbcc.kidly.boundedcontext.recommend.domain.preference;

import java.util.HashMap;
import java.util.Map;

/**
 * 자녀 한 명의 선호 집계다. 카테고리별 활동 횟수와 담기 횟수를 가진다.
 * 더하고 빼면 새 객체를 돌려준다.
 */
public final class ChildPreference {

    private static final int CATEGORY_POINTS = 3;

    private static final int CART_ADD_POINTS = 2;

    private final Map<Long, Integer> categoryActivityCounts;

    private final Map<Long, Integer> categoryCartAddCounts;

    private ChildPreference(Map<Long, Integer> categoryActivityCounts, Map<Long, Integer> categoryCartAddCounts) {
        this.categoryActivityCounts = categoryActivityCounts;
        this.categoryCartAddCounts = categoryCartAddCounts;
    }

    public static ChildPreference empty() {
        // Map.of()는 null 키를 찾으면 예외를 던지므로, 카테고리 · 브랜드가 없는 상품도 찾을 수 있게 HashMap으로 둔다
        return new ChildPreference(new HashMap<>(), new HashMap<>());
    }

    public ChildPreference plus(PreferenceActivity activity) {
        Map<Long, Integer> activityCounts = new HashMap<>(categoryActivityCounts);
        Map<Long, Integer> cartAddCounts = new HashMap<>(categoryCartAddCounts);
        if (activity.categoryId() != null) {
            addCount(activityCounts, activity.categoryId(), 1);
            if (activity.cartAdd()) {
                addCount(cartAddCounts, activity.categoryId(), 1);
            }
        }
        return new ChildPreference(activityCounts, cartAddCounts);
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

    private static <K> void addCount(Map<K, Integer> counts, K key, int delta) {
        counts.put(key, counts.getOrDefault(key, 0) + delta);
    }

    private static <K> boolean appeared(Map<K, Integer> counts, K key) {
        return counts.getOrDefault(key, 0) > 0;
    }
}
