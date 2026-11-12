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

    // 이 횟수 이상 찾아본(상품 조회 · 검색 · 검색 결과 클릭 · 장바구니 담기를 합한) 카테고리 · 브랜드를 「자주 찾아본」으로 친다
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
        List<String> labels = frequentLabels(categoryId, categoryName, brand);
        // 카테고리가 없는 상품은 카테고리 근거를 붙이지 않는다(불변 맵은 null 키로 찾으면 예외를 던진다)
        if (categoryId != null && categoryCartAddCounts.getOrDefault(categoryId, 0) > 0) {
            labels.add("장바구니에 담은 카테고리: " + categoryName);
        }
        if (brand != null && purchasedBrands.contains(brand)) {
            labels.add("산 적 있는 브랜드: " + brand);
        }
        if (categoryId != null && purchasedCategoryIds.contains(categoryId)) {
            labels.add("전에 산 카테고리: " + categoryName);
        }
        return labels;
    }

    // 자주 찾아본 카테고리 · 브랜드를 활동 횟수가 많은 순으로 둔다. 같으면 카테고리를 앞에 둔다
    private List<String> frequentLabels(Long categoryId, String categoryName, String brand) {
        int categoryCount = 0;
        if (categoryId != null) {
            categoryCount = categoryActivityCounts.getOrDefault(categoryId, 0);
        }
        int brandCount = 0;
        if (brand != null) {
            brandCount = brandActivityCounts.getOrDefault(brand, 0);
        }
        List<String> labels = new ArrayList<>();
        String categoryLabel = null;
        if (categoryCount >= FREQUENT_COUNT) {
            categoryLabel = "자주 찾아본 카테고리: " + categoryName;
        }
        String brandLabel = null;
        if (brandCount >= FREQUENT_COUNT) {
            brandLabel = "자주 찾아본 브랜드: " + brand;
        }
        if (brandLabel != null && brandCount > categoryCount) {
            labels.add(brandLabel);
            brandLabel = null;
        }
        if (categoryLabel != null) {
            labels.add(categoryLabel);
        }
        if (brandLabel != null) {
            labels.add(brandLabel);
        }
        return labels;
    }
}
