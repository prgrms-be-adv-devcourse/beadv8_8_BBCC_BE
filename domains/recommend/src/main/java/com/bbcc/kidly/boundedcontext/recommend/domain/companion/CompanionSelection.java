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
        // AI에는 규칙 상위 20개만 넘기므로 그 밖의 상품은 후보 밖으로 본다
        List<CompanionCandidate> sentToAi = ranked.subList(0, Math.min(ranked.size(), CompanionWeights.CANDIDATE_COUNT));
        Map<Long, CompanionCandidate> candidatesByProductId = new HashMap<>();
        for (CompanionCandidate candidate : sentToAi) {
            candidatesByProductId.put(candidate.product().productId(), candidate);
        }
        List<CompanionPick> picks = new ArrayList<>();
        Set<Long> pickedProductIds = new HashSet<>();
        // AI를 부르지 못했거나 실패하면 고른 것이 없으므로 빈 목록으로 본다
        List<CompanionAiPick> requestedPicks = aiPicks;
        if (requestedPicks == null) {
            requestedPicks = List.of();
        }
        for (CompanionAiPick aiPick : requestedPicks) {
            if (picks.size() >= CompanionWeights.PICK_COUNT) {
                break;
            }
            if (aiPick == null) {
                continue;
            }
            CompanionCandidate candidate = candidatesByProductId.get(aiPick.productId());
            if (candidate == null || !pickedProductIds.add(aiPick.productId())) {
                continue;
            }
            picks.add(new CompanionPick(candidate.product(), reasonOrNull(aiPick.reason())));
        }
        CompanionSource source = CompanionSource.AI;
        if (picks.isEmpty()) {
            source = CompanionSource.RULE;
        }
        for (CompanionCandidate candidate : ranked) {
            if (picks.size() >= CompanionWeights.PICK_COUNT) {
                break;
            }
            if (pickedProductIds.add(candidate.product().productId())) {
                picks.add(new CompanionPick(candidate.product(), null));
            }
        }
        return new CompanionSelection(source, picks);
    }

    // 화면에 빈 이유 칸을 보여 주지 않도록 비어 있는 이유는 없는 것으로 본다
    private static String reasonOrNull(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        return reason;
    }
}
