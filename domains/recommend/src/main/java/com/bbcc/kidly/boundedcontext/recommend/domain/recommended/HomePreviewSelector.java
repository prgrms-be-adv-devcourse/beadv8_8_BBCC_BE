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
     * 후보는 20개보다 많이 받아도 앞 20개만 쓴다. AI가 20개 밖의 상품을 골라도 받아들이지 않기 위해서다.
     * AI 답에서 빈 원소, 후보에 없는 상품 ID(null 포함), 이미 나온 상품 ID를 버리고,
     * 남은 것 중 앞에서 4개를 AI 답 순서와 AI가 준 이유 그대로 돌려준다. 이유가 비어 있거나 공백뿐이면 null로 둔다.
     * 남은 것이 4개보다 적으면 빈 자리를 아직 나오지 않은 후보 순서로 채우고, 채운 상품의 이유는 없다.
     * 남은 것이 하나도 없으면 후보 앞에서 4개(4개보다 적으면 있는 만큼)를 이유 없이 규칙 결과로 돌려준다.
     */
    public HomePreview select(List<RecommendedProduct> candidates, List<HomePreviewPick> aiPicks) {
        Objects.requireNonNull(candidates, "candidates는 필수입니다");
        List<RecommendedProduct> topCandidates = candidates(candidates);
        Set<Long> pickedIds = new HashSet<>();
        List<HomePreview.Item> items = validAiItems(topCandidates, aiPicks, pickedIds);
        HomePreviewSource source = HomePreviewSource.AI;
        if (items.isEmpty()) {
            source = HomePreviewSource.RULE;
        }
        fillRemainingSlots(items, topCandidates, pickedIds);
        return new HomePreview(source, items);
    }

    // AI 답에서 후보에 있고 아직 나오지 않은 상품만 앞에서 4개까지 AI 답 순서대로 모은다
    private List<HomePreview.Item> validAiItems(
            List<RecommendedProduct> candidates, List<HomePreviewPick> aiPicks, Set<Long> pickedIds) {
        List<HomePreview.Item> items = new ArrayList<>();
        if (aiPicks == null) {
            return items;
        }
        Map<Long, RecommendedProduct> candidateById = new HashMap<>();
        for (RecommendedProduct candidate : candidates) {
            candidateById.put(candidate.productId(), candidate);
        }
        for (HomePreviewPick pick : aiPicks) {
            if (items.size() == PREVIEW_SIZE) {
                break;
            }
            // AI 응답 JSON 배열에 null이 섞여 오면 빈 원소가 된다
            if (pick == null) {
                continue;
            }
            RecommendedProduct product = candidateById.get(pick.productId());
            if (product == null || !pickedIds.add(pick.productId())) {
                continue;
            }
            items.add(new HomePreview.Item(product, reasonOrNull(pick.reason())));
        }
        return items;
    }

    // 빈 문자열이나 공백뿐인 이유는 이유가 없는 것과 같으므로 null로 둔다
    private String reasonOrNull(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        return reason;
    }

    // 4개가 되거나 후보가 떨어질 때까지 아직 나오지 않은 후보를 후보 순서대로 이유 없이 더한다.
    // 고른 것이 하나도 없으면 후보 앞에서 4개가 된다
    private void fillRemainingSlots(
            List<HomePreview.Item> items, List<RecommendedProduct> candidates, Set<Long> pickedIds) {
        for (RecommendedProduct candidate : candidates) {
            if (items.size() == PREVIEW_SIZE) {
                return;
            }
            if (pickedIds.add(candidate.productId())) {
                items.add(new HomePreview.Item(candidate, null));
            }
        }
    }
}
