package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendedProductFixture.BASE_TIME;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendedProductFixture.recommendedProductRegisteredAt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.bbcc.kidly.boundedcontext.recommend.domain.recommended.HomePreview;
import com.bbcc.kidly.boundedcontext.recommend.domain.recommended.HomePreviewPick;
import com.bbcc.kidly.boundedcontext.recommend.domain.recommended.HomePreviewSelector;
import com.bbcc.kidly.boundedcontext.recommend.domain.recommended.HomePreviewSource;
import com.bbcc.kidly.boundedcontext.recommend.domain.recommended.RecommendedProduct;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class HomePreviewSelectorTest {

    private final HomePreviewSelector selector = new HomePreviewSelector();

    @ParameterizedTest
    @CsvSource({"25, 20", "20, 20", "3, 3", "0, 0"})
    @DisplayName("HOME-01 후보는 선호 점수 순서의 앞에서 20개이고, 20개보다 적으면 있는 만큼이다")
    void candidatesAreTopTwentyInOrder(int rankedCount, int expectedCount) {
        List<RecommendedProduct> ranked = productsWithIds(rankedCount);

        List<RecommendedProduct> candidates = selector.candidates(ranked);

        assertThat(candidates).containsExactlyElementsOf(ranked.subList(0, expectedCount));
    }

    @ParameterizedTest
    @CsvSource({"20, true, 4", "20, false, 4", "4, true, 4", "3, false, 3", "0, true, 0"})
    @DisplayName("HOME-02 AI 결과가 없으면 후보 앞 4개를 규칙 결과로 돌려주고 이유는 없다")
    void ruleResultWithoutAiPicks(int candidateCount, boolean picksNull, int expectedCount) {
        List<RecommendedProduct> candidates = productsWithIds(candidateCount);
        List<HomePreviewPick> aiPicks = picksNull ? null : List.of();

        HomePreview preview = selector.select(candidates, aiPicks);

        assertThat(preview.source()).isEqualTo(HomePreviewSource.RULE);
        assertThat(preview.items()).extracting(HomePreview.Item::product)
            .containsExactlyElementsOf(candidates.subList(0, expectedCount));
        assertThat(preview.items()).allSatisfy(item -> assertThat(item.reason()).isNull());
    }

    @Test
    @DisplayName("HOME-03 AI 답에서 빈 원소, 후보에 없는 ID, 이미 나온 ID는 버리고 남은 것 중 앞 4개만 쓴다")
    void dropInvalidAndDuplicatePicks() {
        List<RecommendedProduct> candidates = productsWithIds(10);
        List<HomePreviewPick> aiPicks = Arrays.asList(
            new HomePreviewPick(99L, "후보 밖"),
            null,
            new HomePreviewPick(null, "ID 없음"),
            new HomePreviewPick(5L, "이유 5이에요."),
            new HomePreviewPick(5L, "이유 5 다시"),
            new HomePreviewPick(7L, "이유 7이에요."),
            new HomePreviewPick(2L, "이유 2이에요."),
            new HomePreviewPick(9L, "이유 9이에요."),
            new HomePreviewPick(3L, "이유 3이에요."));

        HomePreview preview = selector.select(candidates, aiPicks);

        assertThat(preview.source()).isEqualTo(HomePreviewSource.AI);
        assertThat(preview.items())
            .extracting(item -> item.product().productId(), HomePreview.Item::reason)
            .containsExactly(
                tuple(5L, "이유 5이에요."), tuple(7L, "이유 7이에요."), tuple(2L, "이유 2이에요."), tuple(9L, "이유 9이에요."));
    }

    @Test
    @DisplayName("HOME-03 AI 답에서 남은 것이 하나도 없으면 후보 앞 4개를 규칙 결과로 돌려준다")
    void ruleResultWhenNoValidPick() {
        List<RecommendedProduct> candidates = productsWithIds(6);
        List<HomePreviewPick> aiPicks = List.of(new HomePreviewPick(99L, "후보 밖"), new HomePreviewPick(null, "ID 없음"));

        HomePreview preview = selector.select(candidates, aiPicks);

        assertThat(preview.source()).isEqualTo(HomePreviewSource.RULE);
        assertThat(preview.items())
            .extracting(item -> item.product().productId(), HomePreview.Item::reason)
            .containsExactly(tuple(1L, null), tuple(2L, null), tuple(3L, null), tuple(4L, null));
    }

    @Test
    @DisplayName("HOME-04 AI가 고른 것이 4개보다 적으면 빈 자리를 고르지 않은 후보 순서로 채우고 채운 상품의 이유는 없다")
    void fillRemainingSlotsInCandidateOrder() {
        List<RecommendedProduct> candidates = productsWithIds(10);
        List<HomePreviewPick> aiPicks = List.of(
            new HomePreviewPick(7L, "이유 7이에요."), new HomePreviewPick(1L, "이유 1이에요."));

        HomePreview preview = selector.select(candidates, aiPicks);

        assertThat(preview.source()).isEqualTo(HomePreviewSource.AI);
        assertThat(preview.items())
            .extracting(item -> item.product().productId(), HomePreview.Item::reason)
            .containsExactly(tuple(7L, "이유 7이에요."), tuple(1L, "이유 1이에요."), tuple(2L, null), tuple(3L, null));
    }

    @Test
    @DisplayName("HOME-03 후보를 20개보다 많이 넘겨도 앞 20개 밖의 상품을 고른 AI 답은 버린다")
    void dropPicksBeyondTopTwenty() {
        List<RecommendedProduct> candidates = productsWithIds(25);
        List<HomePreviewPick> aiPicks = List.of(
            new HomePreviewPick(22L, "이유 22이에요."), new HomePreviewPick(3L, "이유 3이에요."));

        HomePreview preview = selector.select(candidates, aiPicks);

        assertThat(preview.source()).isEqualTo(HomePreviewSource.AI);
        assertThat(preview.items())
            .extracting(item -> item.product().productId(), HomePreview.Item::reason)
            .containsExactly(tuple(3L, "이유 3이에요."), tuple(1L, null), tuple(2L, null), tuple(4L, null));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    @DisplayName("HOME-03 AI가 준 이유가 없거나 빈 문자열 · 공백이면 상품은 쓰고 이유만 null로 둔다")
    void blankReasonBecomesNull(String reason) {
        List<RecommendedProduct> candidates = productsWithIds(4);
        List<HomePreviewPick> aiPicks = List.of(new HomePreviewPick(2L, reason));

        HomePreview preview = selector.select(candidates, aiPicks);

        assertThat(preview.source()).isEqualTo(HomePreviewSource.AI);
        assertThat(preview.items().getFirst().product().productId()).isEqualTo(2L);
        assertThat(preview.items().getFirst().reason()).isNull();
    }

    @Test
    @DisplayName("HOME-04 후보가 4개보다 적으면 AI가 고른 것 뒤에 남은 후보를 있는 만큼만 채운다")
    void fillUpToCandidateCount() {
        List<RecommendedProduct> candidates = productsWithIds(2);
        List<HomePreviewPick> aiPicks = List.of(new HomePreviewPick(2L, "이유 2이에요."));

        HomePreview preview = selector.select(candidates, aiPicks);

        assertThat(preview.source()).isEqualTo(HomePreviewSource.AI);
        assertThat(preview.items())
            .extracting(item -> item.product().productId(), HomePreview.Item::reason)
            .containsExactly(tuple(2L, "이유 2이에요."), tuple(1L, null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"함께 입기 좋아요.", "함께 입기 좋아요", "산뜻하게 입기 좋아요!", "단정한 느낌을 줍니다.",
        "함께 입어 보시겠어요?", "가을 나들이룩으로 추천", "가을 나들이룩으로 추천.", "등원룩으로 활용"})
    @DisplayName("HOME-06 경어체이거나 「~로 추천」 · 「~로 활용」으로 끝나는 이유는 그대로 쓴다")
    void keepPoliteOrRecommendEndingReason(String reason) {
        HomePreview preview = selector.select(productsWithIds(4), List.of(new HomePreviewPick(2L, reason)));

        assertThat(preview.items().getFirst().reason()).isEqualTo(reason);
    }

    @ParameterizedTest
    @ValueSource(strings = {"함께 입기 좋다.", "코디 완성", "등원룩으로 추천해", "함께 입기 좋아요?"})
    @DisplayName("HOME-06 경어체도 「~로 추천」 · 「~로 활용」도 아닌 이유는 상품은 쓰고 이유만 null로 둔다")
    void hideReasonWithOtherEnding(String reason) {
        HomePreview preview = selector.select(productsWithIds(4), List.of(new HomePreviewPick(2L, reason)));

        assertThat(preview.items().getFirst().product().productId()).isEqualTo(2L);
        assertThat(preview.items().getFirst().reason()).isNull();
    }

    // 상품 ID를 1부터 차례로 붙인 상품 목록이다. 목록 순서가 선호 점수 순서다
    private static List<RecommendedProduct> productsWithIds(int count) {
        List<RecommendedProduct> products = new ArrayList<>();
        for (long id = 1; id <= count; id++) {
            products.add(recommendedProductRegisteredAt(id, BASE_TIME));
        }
        return products;
    }
}
