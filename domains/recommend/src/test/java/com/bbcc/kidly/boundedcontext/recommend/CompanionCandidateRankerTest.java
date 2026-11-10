package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.domain.companion.CompanionKind.BOTTOM;
import static com.bbcc.kidly.boundedcontext.recommend.domain.companion.CompanionKind.ONE_PIECE;
import static com.bbcc.kidly.boundedcontext.recommend.domain.companion.CompanionKind.OUTER;
import static com.bbcc.kidly.boundedcontext.recommend.domain.companion.CompanionKind.TOP;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.domain.companion.CompanionCandidate;
import com.bbcc.kidly.boundedcontext.recommend.domain.companion.CompanionCandidateRanker;
import com.bbcc.kidly.boundedcontext.recommend.domain.companion.CompanionKind;
import com.bbcc.kidly.boundedcontext.recommend.domain.recommended.RecommendedProduct;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CompanionCandidateRankerTest {

    private static final LocalDateTime SALE_STARTED_AT = LocalDateTime.of(2026, 11, 1, 10, 0);

    private final CompanionCandidateRanker ranker = new CompanionCandidateRanker();

    @Test
    @DisplayName("PAIR-01 보고 있는 상품과 짝이 아닌 종류(같은 종류 포함)의 상품은 후보에서 빠진다")
    void excludeKindsThatAreNotPairs() {
        List<CompanionCandidate> candidates = List.of(
            candidate(1L, BOTTOM, 0.5),
            candidate(2L, ONE_PIECE, 0.9),
            candidate(3L, TOP, 0.9),
            candidate(4L, OUTER, 0.5));

        List<CompanionCandidate> ranked = ranker.rank(TOP, candidates);

        assertThat(productIds(ranked)).containsExactlyInAnyOrder(1L, 4L);
    }

    @Test
    @DisplayName("PAIR-02 후보는 0.3 × 유사도 + 0.7 × 짝 우선순위 점수 순이라, 유사도가 높은 아우터가 유사도 0인 하의보다 앞설 수 있다")
    void rankByWeightedSumOfSimilarityAndPairPriority() {
        // 상의의 짝은 하의(1), 아우터(2/3), 잡화(1/3) 순이다
        List<CompanionCandidate> candidates = List.of(
            candidate(1L, CompanionKind.ACCESSORY, 0.5),
            candidate(2L, BOTTOM, 0.0),
            candidate(3L, OUTER, 1.0));

        List<CompanionCandidate> ranked = ranker.rank(TOP, candidates);

        assertThat(productIds(ranked)).containsExactly(3L, 2L, 1L);
    }

    private static CompanionCandidate candidate(Long productId, CompanionKind kind, Double similarity) {
        return candidate(productId, kind, similarity, SALE_STARTED_AT);
    }

    private static CompanionCandidate candidate(Long productId, CompanionKind kind, Double similarity,
        LocalDateTime saleStartedAt) {
        RecommendedProduct product = new RecommendedProduct(productId, 100L, "상품 " + productId, null, 10_000L, "110",
            1L, null, saleStartedAt, saleStartedAt);
        return new CompanionCandidate(product, kind, similarity);
    }

    private static List<Long> productIds(List<CompanionCandidate> candidates) {
        return candidates.stream().map(candidate -> candidate.product().productId()).toList();
    }
}
