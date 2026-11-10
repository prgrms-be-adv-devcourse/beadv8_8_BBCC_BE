package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendedProductFixture.BASE_TIME;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendedProductFixture.recommendedProductRegisteredAt;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.domain.recommended.HomePreview;
import com.bbcc.kidly.boundedcontext.recommend.domain.recommended.HomePreviewPick;
import com.bbcc.kidly.boundedcontext.recommend.domain.recommended.HomePreviewSelector;
import com.bbcc.kidly.boundedcontext.recommend.domain.recommended.HomePreviewSource;
import com.bbcc.kidly.boundedcontext.recommend.domain.recommended.RecommendedProduct;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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

    // 상품 ID를 1부터 차례로 붙인 상품 목록이다. 목록 순서가 선호 점수 순서다
    private static List<RecommendedProduct> productsWithIds(int count) {
        List<RecommendedProduct> products = new ArrayList<>();
        for (long id = 1; id <= count; id++) {
            products.add(recommendedProductRegisteredAt(id, BASE_TIME));
        }
        return products;
    }
}
