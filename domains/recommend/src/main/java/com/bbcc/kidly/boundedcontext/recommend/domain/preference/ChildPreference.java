package com.bbcc.kidly.boundedcontext.recommend.domain.preference;

import java.util.HashMap;
import java.util.Map;

/**
 * 자녀 한 명의 선호 집계다. 카테고리별 활동 횟수를 가진다.
 * 더하고 빼면 새 객체를 돌려준다.
 */
public final class ChildPreference {

    private static final int CATEGORY_POINTS = 3;

    private final Map<Long, Integer> categoryActivityCounts;

    private ChildPreference(Map<Long, Integer> categoryActivityCounts) {
        this.categoryActivityCounts = categoryActivityCounts;
    }

    public static ChildPreference empty() {
        return new ChildPreference(Map.of());
    }

    public ChildPreference plus(PreferenceActivity activity) {
        Map<Long, Integer> categoryCounts = new HashMap<>(categoryActivityCounts);
        if (activity.categoryId() != null) {
            categoryCounts.put(activity.categoryId(), categoryCounts.getOrDefault(activity.categoryId(), 0) + 1);
        }
        return new ChildPreference(categoryCounts);
    }

    public int categoryPoints(Long categoryId) {
        if (categoryActivityCounts.getOrDefault(categoryId, 0) > 0) {
            return CATEGORY_POINTS;
        }
        return 0;
    }
}
