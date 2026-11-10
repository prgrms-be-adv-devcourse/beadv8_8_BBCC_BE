package com.bbcc.kidly.boundedcontext.recommend.domain.recommended;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 홈 화면 맛보기에 보여 줄 상품을 고른다.
 */
public class HomePreviewSelector {

    // 홈 맛보기 후보는 선호 점수 순서의 앞에서 20개다
    private static final int CANDIDATE_LIMIT = 20;

    /**
     * 선호 점수 순서로 정렬된 목록에서 앞의 20개를 순서 그대로 돌려준다.
     * 20개보다 적으면 있는 만큼 모두 돌려준다.
     */
    public List<RecommendedProduct> candidates(List<RecommendedProduct> ranked) {
        Objects.requireNonNull(ranked, "ranked는 필수입니다");
        int end = Math.min(ranked.size(), CANDIDATE_LIMIT);
        return new ArrayList<>(ranked.subList(0, end));
    }
}
