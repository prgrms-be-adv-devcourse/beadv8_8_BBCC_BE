package com.bbcc.kidly.boundedcontext.recommend.domain.preference;

import static java.util.Objects.requireNonNull;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 자녀 또는 회원 한 명의 선호를 한 번 계산한 결과다. 취향 벡터를 만들 활동이 없으면 tasteVector는 null이다.
 */
public record PreferenceSnapshot(
        List<CategoryAffinity> categories,
        List<BrandAffinity> brands,
        TasteVector tasteVector,
        LocalDateTime calculatedAt
) {

    /**
     * 계산한 결과가 없는 상태다.
     */
    public static PreferenceSnapshot empty() {
        return new PreferenceSnapshot(List.of(), List.of(), null, null);
    }

    /**
     * 자녀의 결과에 자녀를 고르지 않은 회원 활동의 결과를 낮춘 배율로 합친다.
     * 형제가 있는 회원의 활동이 고른 자녀의 순서를 흐리지 않으면서, 자녀를 고르지 않는 회원도 맞춤 순서를 받게 한다.
     */
    public static PreferenceSnapshot combine(PreferenceSnapshot child, PreferenceSnapshot member) {
        requireNonNull(child, "child는 필수입니다");
        requireNonNull(member, "member는 필수입니다");
        AffinityAccumulator<Long> categoryAffinities = new AffinityAccumulator<>();
        AffinityAccumulator<String> brandAffinities = new AffinityAccumulator<>();
        for (CategoryAffinity category : child.categories) {
            categoryAffinities.add(category.categoryId(), category.score());
        }
        for (CategoryAffinity category : member.categories) {
            categoryAffinities.add(category.categoryId(), PreferenceWeights.MEMBER_SHARE * category.score());
        }
        for (BrandAffinity brand : child.brands) {
            brandAffinities.add(brand.brand(), brand.score());
        }
        for (BrandAffinity brand : member.brands) {
            brandAffinities.add(brand.brand(), PreferenceWeights.MEMBER_SHARE * brand.score());
        }
        TasteVectorSum tasteVectorSum = new TasteVectorSum();
        if (child.tasteVector != null) {
            TasteVector taste = child.tasteVector;
            tasteVectorSum.add(taste.embedding(), taste.weightSum(), taste.activityCount());
        }
        if (member.tasteVector != null) {
            TasteVector taste = member.tasteVector;
            tasteVectorSum.add(
                    taste.embedding(), PreferenceWeights.MEMBER_SHARE * taste.weightSum(), taste.activityCount());
        }
        LocalDateTime calculatedAt = child.calculatedAt != null ? child.calculatedAt : member.calculatedAt;
        return of(categoryAffinities, brandAffinities, tasteVectorSum.toTasteVector(), calculatedAt);
    }

    static PreferenceSnapshot of(
            AffinityAccumulator<Long> categoryAffinities,
            AffinityAccumulator<String> brandAffinities,
            TasteVector tasteVector,
            LocalDateTime calculatedAt
    ) {
        List<CategoryAffinity> categories = new ArrayList<>();
        for (Map.Entry<Long, Double> entry : categoryAffinities.affinities().entrySet()) {
            categories.add(new CategoryAffinity(entry.getKey(), entry.getValue()));
        }
        List<BrandAffinity> brands = new ArrayList<>();
        for (Map.Entry<String, Double> entry : brandAffinities.affinities().entrySet()) {
            brands.add(new BrandAffinity(entry.getKey(), entry.getValue()));
        }
        return new PreferenceSnapshot(List.copyOf(categories), List.copyOf(brands), tasteVector, calculatedAt);
    }

    /**
     * 카테고리의 친밀도다. 계산한 결과에 없는 카테고리는 0이다.
     */
    public double categoryAffinity(Long categoryId) {
        for (CategoryAffinity category : categories) {
            if (category.categoryId().equals(categoryId)) {
                return category.score();
            }
        }
        return 0;
    }

    /**
     * 브랜드의 친밀도다. 글자가 같은 브랜드만 일치하고, 계산한 결과에 없는 브랜드는 0이다.
     */
    public double brandAffinity(String brand) {
        for (BrandAffinity affinity : brands) {
            if (affinity.brand().equals(brand)) {
                return affinity.score();
            }
        }
        return 0;
    }
}
