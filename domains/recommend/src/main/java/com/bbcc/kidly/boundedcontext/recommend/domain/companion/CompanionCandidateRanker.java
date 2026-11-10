package com.bbcc.kidly.boundedcontext.recommend.domain.companion;

import java.util.ArrayList;
import java.util.List;

/** 보고 있는 상품과 짝이 되는 종류의 후보만 골라 순서를 정한다. */
public class CompanionCandidateRanker {

    public List<CompanionCandidate> rank(CompanionKind viewed, List<CompanionCandidate> candidates) {
        List<CompanionKind> pairKinds = CompanionPairs.of(viewed);
        List<CompanionCandidate> pairs = new ArrayList<>();
        for (CompanionCandidate candidate : candidates) {
            if (pairKinds.contains(candidate.kind())) {
                pairs.add(candidate);
            }
        }
        return pairs;
    }
}
