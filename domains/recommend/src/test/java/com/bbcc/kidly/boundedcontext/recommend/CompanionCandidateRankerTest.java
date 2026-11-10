package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.domain.companion.CompanionCandidate;
import com.bbcc.kidly.boundedcontext.recommend.domain.companion.CompanionCandidateRanker;
import com.bbcc.kidly.boundedcontext.recommend.domain.recommended.RecommendedProduct;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CompanionCandidateRankerTest {

    private static final LocalDateTime SALE_STARTED_AT = LocalDateTime.of(2026, 11, 1, 10, 0);

    private static final List<String> TOP = List.of("의류", "상의");
    private static final List<String> BOTTOM = List.of("의류", "하의");
    private static final List<String> ONE_PIECE = List.of("의류", "원피스");
    private static final List<String> OUTER = List.of("의류", "아우터");
    private static final List<String> ACCESSORY = List.of("잡화", "모자");
    private static final List<String> UNKNOWN = List.of("의류", "내의");

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
    @DisplayName("PAIR-01 카테고리로 종류를 정할 수 없는 후보는 빠진다")
    void excludeCandidatesWithoutKind() {
        List<CompanionCandidate> candidates = List.of(
            candidate(1L, BOTTOM, 0.5),
            candidate(2L, UNKNOWN, 0.9),
            candidate(3L, List.of(), 0.9));

        List<CompanionCandidate> ranked = ranker.rank(TOP, candidates);

        assertThat(productIds(ranked)).containsExactly(1L);
    }

    @Test
    @DisplayName("PAIR-01 보고 있는 상품의 종류를 정할 수 없으면 후보가 없다")
    void noCandidatesWhenViewedProductHasNoKind() {
        List<CompanionCandidate> candidates = List.of(candidate(1L, BOTTOM, 0.5), candidate(2L, TOP, 0.5));

        assertThat(ranker.rank(UNKNOWN, candidates)).isEmpty();
        assertThat(ranker.rank(List.of(), candidates)).isEmpty();
    }

    @Test
    @DisplayName("PAIR-02 후보는 0.3 × 유사도 + 0.7 × 짝 우선순위 점수 순이라, 유사도가 높은 아우터가 유사도 0인 하의보다 앞설 수 있다")
    void rankByWeightedSumOfSimilarityAndPairPriority() {
        // 상의의 짝은 하의(1), 아우터(2/3), 잡화(1/3) 순이다
        List<CompanionCandidate> candidates = List.of(
            candidate(1L, ACCESSORY, 0.5),
            candidate(2L, BOTTOM, 0.0),
            candidate(3L, OUTER, 1.0));

        List<CompanionCandidate> ranked = ranker.rank(TOP, candidates);

        assertThat(productIds(ranked)).containsExactly(3L, 2L, 1L);
    }

    @Test
    @DisplayName("PAIR-02 같은 짝 종류 안에서는 유사도가 높은 상품이 앞이고, 벡터가 없어 유사도가 없는 상품은 유사도 0으로 본다")
    void rankSameKindBySimilarityTreatingMissingAsZero() {
        List<CompanionCandidate> candidates = List.of(
            candidate(1L, BOTTOM, 0.2),
            candidate(2L, BOTTOM, null),
            candidate(3L, BOTTOM, 0.8));

        List<CompanionCandidate> ranked = ranker.rank(TOP, candidates);

        assertThat(productIds(ranked)).containsExactly(3L, 1L, 2L);
    }

    @Test
    @DisplayName("PAIR-02 점수가 같으면 판매 시작 시각이 늦은 상품이 앞이고, 그것도 같으면 상품 ID가 큰 상품이 앞이다")
    void breakTiesByLaterSaleStartThenLargerId() {
        LocalDateTime later = SALE_STARTED_AT.plusDays(4);
        List<CompanionCandidate> candidates = List.of(
            candidate(1L, BOTTOM, 0.5, later),
            candidate(2L, BOTTOM, 0.5, SALE_STARTED_AT),
            candidate(3L, BOTTOM, 0.5, SALE_STARTED_AT));

        List<CompanionCandidate> ranked = ranker.rank(TOP, candidates);

        assertThat(productIds(ranked)).containsExactly(1L, 3L, 2L);
    }

    @Test
    @DisplayName("후보가 20개보다 많으면 점수가 높은 20개만 AI에 넘길 후보로 남긴다")
    void keepTopTwentyCandidates() {
        List<CompanionCandidate> candidates = new ArrayList<>();
        for (long id = 1; id <= 25; id++) {
            candidates.add(candidate(id, BOTTOM, id / 100.0));
        }

        List<CompanionCandidate> ranked = ranker.rank(TOP, candidates);

        assertThat(ranked).hasSize(20);
        assertThat(productIds(ranked)).first().isEqualTo(25L);
        assertThat(productIds(ranked)).last().isEqualTo(6L);
    }

    private static CompanionCandidate candidate(Long productId, List<String> categoryPathNames, Double similarity) {
        return candidate(productId, categoryPathNames, similarity, SALE_STARTED_AT);
    }

    private static CompanionCandidate candidate(Long productId, List<String> categoryPathNames, Double similarity,
        LocalDateTime saleStartedAt) {
        RecommendedProduct product = new RecommendedProduct(productId, 100L, "상품 " + productId, null, 10_000L, "110",
            1L, null, saleStartedAt, saleStartedAt);
        return new CompanionCandidate(product, categoryPathNames, similarity);
    }

    private static List<Long> productIds(List<CompanionCandidate> candidates) {
        return candidates.stream().map(candidate -> candidate.product().productId()).toList();
    }
}
