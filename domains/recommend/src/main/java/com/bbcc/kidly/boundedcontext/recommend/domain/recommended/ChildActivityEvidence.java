package com.bbcc.kidly.boundedcontext.recommend.domain.recommended;

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 자녀 활동으로 홈 맛보기 후보마다 고른 이유의 근거 라벨을 만든다(HOME-05).
 * 기간은 부르는 쪽이 자른 값을 받는다. 본 · 담은 활동은 최근 30일, 구매는 최근 3개월이다.
 */
public final class ChildActivityEvidence {

    // 이 횟수 이상 본 카테고리 · 브랜드를 「자주 본」으로 친다
    static final int FREQUENT_COUNT = 5;

    private final Map<Long, Integer> categoryActivityCounts;
    private final Map<Long, Integer> categoryCartAddCounts;
    private final Map<String, Integer> brandActivityCounts;
    private final Set<String> purchasedBrands;
    private final Set<Long> purchasedCategoryIds;

    public ChildActivityEvidence(
        Map<Long, Integer> categoryActivityCounts,
        Map<Long, Integer> categoryCartAddCounts,
        Map<String, Integer> brandActivityCounts,
        Set<String> purchasedBrands,
        Set<Long> purchasedCategoryIds
    ) {
        this.categoryActivityCounts = Map.copyOf(
            requireNonNull(categoryActivityCounts, "categoryActivityCounts는 필수입니다"));
        this.categoryCartAddCounts = Map.copyOf(
            requireNonNull(categoryCartAddCounts, "categoryCartAddCounts는 필수입니다"));
        this.brandActivityCounts = Map.copyOf(requireNonNull(brandActivityCounts, "brandActivityCounts는 필수입니다"));
        this.purchasedBrands = Set.copyOf(requireNonNull(purchasedBrands, "purchasedBrands는 필수입니다"));
        this.purchasedCategoryIds = Set.copyOf(
            requireNonNull(purchasedCategoryIds, "purchasedCategoryIds는 필수입니다"));
    }

    /**
     * 후보 하나의 근거 라벨이다. 맞는 근거가 없으면 빈 목록이다.
     */
    public List<String> labelsFor(Long categoryId, String categoryName, String brand) {
        List<String> labels = new ArrayList<>();
        if (categoryActivityCounts.getOrDefault(categoryId, 0) >= FREQUENT_COUNT) {
            labels.add("자주 본 카테고리: " + categoryName);
        }
        return labels;
    }
}
