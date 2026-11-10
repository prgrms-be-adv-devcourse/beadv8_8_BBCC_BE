package com.bbcc.kidly.boundedcontext.recommend.domain.companion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 보고 있는 상품과 짝이 되는 종류의 후보만 골라 점수가 높은 순으로 정한다. */
public class CompanionCandidateRanker {

    public List<CompanionCandidate> rank(CompanionKind viewed, List<CompanionCandidate> candidates) {
        List<CompanionKind> pairKinds = CompanionPairs.of(viewed);
        List<CompanionCandidate> pairs = new ArrayList<>();
        for (CompanionCandidate candidate : candidates) {
            if (pairKinds.contains(candidate.kind())) {
                pairs.add(candidate);
            }
        }
        // 점수가 같으면 더보기 목록과 같이 판매 시작 시각이 늦은 상품, 그다음 상품 ID가 큰 상품이 앞이다
        pairs.sort(Comparator.comparingDouble((CompanionCandidate candidate) -> score(candidate, pairKinds))
            .thenComparing(candidate -> candidate.product().saleStartedAt())
            .thenComparing(candidate -> candidate.product().productId())
            .reversed());
        return List.copyOf(pairs.subList(0, Math.min(pairs.size(), CompanionWeights.CANDIDATE_COUNT)));
    }

    private double score(CompanionCandidate candidate, List<CompanionKind> pairKinds) {
        return CompanionWeights.SIMILARITY * similarityOrZero(candidate)
            + CompanionWeights.PAIR_PRIORITY * pairPriorityScore(candidate.kind(), pairKinds);
    }

    // 상품 벡터가 아직 없어 유사도를 모르는 후보는 짝 우선순위로만 순서를 정한다
    private double similarityOrZero(CompanionCandidate candidate) {
        if (candidate.similarity() == null) {
            return 0.0;
        }
        return candidate.similarity();
    }

    // 짝 종류가 n개일 때 우선순위 i번째(0부터)의 점수는 (n - i) / n이다.
    private double pairPriorityScore(CompanionKind kind, List<CompanionKind> pairKinds) {
        int size = pairKinds.size();
        return (double) (size - pairKinds.indexOf(kind)) / size;
    }
}
