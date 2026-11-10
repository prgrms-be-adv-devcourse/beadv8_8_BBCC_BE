package com.bbcc.kidly.boundedcontext.recommend.domain.recommended;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 홈 화면 맛보기에 보여 줄 상품을 고른다.
 */
public class HomePreviewSelector {

    // 홈 맛보기 후보는 선호 점수 순서의 앞에서 20개다
    private static final int CANDIDATE_LIMIT = 20;

    // 홈 맛보기에는 상품을 최대 4개 보여 준다
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
     * 홈 맛보기 결과를 고른다. aiPicks는 AI 결과가 없으면 null이거나 비어 있을 수 있다.
     * AI 답에서 후보에 없는 상품 ID(null 포함)와 이미 나온 상품 ID를 버리고,
     * 남은 것 중 앞에서 4개를 AI 답 순서와 AI가 준 이유 그대로 돌려준다.
     * 남은 것이 하나도 없으면 후보 앞에서 4개(4개보다 적으면 있는 만큼)를 이유 없이 규칙 결과로 돌려준다.
     */
    public HomePreview select(List<RecommendedProduct> candidates, List<HomePreviewPick> aiPicks) {
        Objects.requireNonNull(candidates, "candidates는 필수입니다");
        List<HomePreview.Item> aiItems = validAiItems(candidates, aiPicks);
        if (!aiItems.isEmpty()) {
            return new HomePreview(HomePreviewSource.AI, aiItems);
        }
        return new HomePreview(HomePreviewSource.RULE, ruleItems(candidates));
    }

    // AI 답에서 후보에 있고 아직 나오지 않은 상품만 앞에서 4개까지 AI 답 순서대로 모은다
    private List<HomePreview.Item> validAiItems(List<RecommendedProduct> candidates, List<HomePreviewPick> aiPicks) {
        List<HomePreview.Item> items = new ArrayList<>();
        if (aiPicks == null) {
            return items;
        }
        Map<Long, RecommendedProduct> candidateById = new HashMap<>();
        for (RecommendedProduct candidate : candidates) {
            candidateById.put(candidate.productId(), candidate);
        }
        Set<Long> pickedIds = new HashSet<>();
        for (HomePreviewPick pick : aiPicks) {
            if (items.size() == PREVIEW_SIZE) {
                break;
            }
            RecommendedProduct product = candidateById.get(pick.productId());
            if (product == null || !pickedIds.add(pick.productId())) {
                continue;
            }
            items.add(new HomePreview.Item(product, pick.reason()));
        }
        return items;
    }

    // 후보 앞에서 4개를 이유 없이 모은다
    private List<HomePreview.Item> ruleItems(List<RecommendedProduct> candidates) {
        List<HomePreview.Item> items = new ArrayList<>();
        int end = Math.min(candidates.size(), PREVIEW_SIZE);
        for (int i = 0; i < end; i++) {
            items.add(new HomePreview.Item(candidates.get(i), null));
        }
        return items;
    }
}
