package com.bbcc.kidly.boundedcontext.recommend.domain.companion;

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 함께 사면 좋은 상품으로 고른 결과다. 출처와, 보여 줄 순서대로 정렬된 상품을 담는다.
 */
public record CompanionSelection(CompanionSource source, List<CompanionPick> picks) {

    public CompanionSelection {
        requireNonNull(source, "source는 필수입니다");
        requireNonNull(picks, "picks는 필수입니다");
    }

    /**
     * AI가 고른 순서대로 담되, 후보에 없는 상품과 이미 고른 상품은 버린다.
     * 고른 수가 모자라면 규칙 순서(ranked 순서)에서 아직 고르지 않은 상품을 이유 없이 채운다.
     */
    public static CompanionSelection of(List<CompanionAiPick> aiPicks, List<CompanionCandidate> ranked) {
        Map<Long, CompanionCandidate> candidatesByProductId = new HashMap<>();
        for (CompanionCandidate candidate : ranked) {
            candidatesByProductId.put(candidate.product().productId(), candidate);
        }
        List<CompanionPick> picks = new ArrayList<>();
        Set<Long> pickedProductIds = new HashSet<>();
        for (CompanionAiPick aiPick : aiPicks) {
            CompanionCandidate candidate = candidatesByProductId.get(aiPick.productId());
            if (candidate == null || !pickedProductIds.add(aiPick.productId())) {
                continue;
            }
            picks.add(new CompanionPick(candidate.product(), aiPick.reason()));
        }
        for (CompanionCandidate candidate : ranked) {
            if (picks.size() >= CompanionWeights.PICK_COUNT) {
                break;
            }
            if (pickedProductIds.add(candidate.product().productId())) {
                picks.add(new CompanionPick(candidate.product(), null));
            }
        }
        return new CompanionSelection(CompanionSource.AI, picks);
    }
}
