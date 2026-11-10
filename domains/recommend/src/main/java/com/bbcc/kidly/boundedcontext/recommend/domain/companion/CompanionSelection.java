package com.bbcc.kidly.boundedcontext.recommend.domain.companion;

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 함께 사면 좋은 상품으로 고른 결과다. 출처와, 보여 줄 순서대로 정렬된 상품을 담는다.
 */
public record CompanionSelection(CompanionSource source, List<CompanionPick> picks) {

    public CompanionSelection {
        requireNonNull(source, "source는 필수입니다");
        requireNonNull(picks, "picks는 필수입니다");
    }

    public static CompanionSelection of(List<CompanionAiPick> aiPicks, List<CompanionCandidate> ranked) {
        Map<Long, CompanionCandidate> candidatesByProductId = new HashMap<>();
        for (CompanionCandidate candidate : ranked) {
            candidatesByProductId.put(candidate.product().productId(), candidate);
        }
        List<CompanionPick> picks = new ArrayList<>();
        for (CompanionAiPick aiPick : aiPicks) {
            CompanionCandidate candidate = candidatesByProductId.get(aiPick.productId());
            picks.add(new CompanionPick(candidate.product(), aiPick.reason()));
        }
        return new CompanionSelection(CompanionSource.AI, picks);
    }
}
