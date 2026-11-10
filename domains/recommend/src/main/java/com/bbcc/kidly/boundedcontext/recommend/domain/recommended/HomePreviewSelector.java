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

    // AI 결과가 없으면 후보 앞에서 4개를 규칙 결과로 보여 준다
    private static final int PREVIEW_SIZE = 4;

    /**
     * 선호 점수 순서로 정렬된 목록에서 앞의 20개를 순서 그대로 돌려준다.
     * 20개보다 적으면 있는 만큼 모두 돌려준다.
     */
    public List<RecommendedProduct> candidates(List<RecommendedProduct> ranked) {
        Objects.requireNonNull(ranked, "ranked는 필수입니다");
        int end = Math.min(ranked.size(), CANDIDATE_LIMIT);
        return new ArrayList<>(ranked.subList(0, end));
    }

    /**
     * 홈 맛보기 결과를 고른다. aiPicks가 null이거나 비어 있으면 AI 결과가 없다는 뜻이므로,
     * 후보 앞에서 4개(4개보다 적으면 있는 만큼)를 이유 없이 규칙 결과로 돌려준다.
     */
    public HomePreview select(List<RecommendedProduct> candidates, List<HomePreviewPick> aiPicks) {
        Objects.requireNonNull(candidates, "candidates는 필수입니다");
        List<HomePreview.Item> items = new ArrayList<>();
        int end = Math.min(candidates.size(), PREVIEW_SIZE);
        for (int i = 0; i < end; i++) {
            items.add(new HomePreview.Item(candidates.get(i), null));
        }
        return new HomePreview(HomePreviewSource.RULE, items);
    }
}
