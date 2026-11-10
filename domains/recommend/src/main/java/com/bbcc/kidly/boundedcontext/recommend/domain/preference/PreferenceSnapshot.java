package com.bbcc.kidly.boundedcontext.recommend.domain.preference;

import java.time.LocalDateTime;
import java.util.List;

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
}
