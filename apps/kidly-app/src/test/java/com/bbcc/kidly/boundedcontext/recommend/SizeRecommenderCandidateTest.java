package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.TODAY;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.childSizeInputWithHeight;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.productA;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.domain.size.ProductSizes;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.SizeRange;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.SizeRecommendation;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.SizeRecommender;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 사이즈 추천 결과가 화면 사이즈표에 쓸 후보 사이즈를 계산과 같은 기준으로 내보내는지 확인한다.
 * 상품 A는 110(권장 키 105~115cm), 120(115~125cm)이다.
 */
class SizeRecommenderCandidateTest {

    private final SizeRecommender recommender = RecommendFixture.recommender();

    @ParameterizedTest(name = "키 {0} → 후보 {1}")
    @CsvSource({
        "115, '110,120'",
        "116, 120",
        "104, 110",
        "103, 110",
        "126, 120",
        "127, 120"})
    @DisplayName("INV-R04 후보 사이즈는 권장 키 범위 안 사이즈가 있으면 그 사이즈들, 없으면 2cm 안 사이즈이고 크기 순이며 추천 사이즈를 담는다")
    void candidatesBySize(double heightCm, String expected) {
        SizeRecommendation result = recommender.recommend(childSizeInputWithHeight(heightCm), productA(), TODAY);

        assertThat(result.getCandidateSizes()).containsExactly(expected.split(","));
        assertThat(result.getCandidateSizes()).contains(result.getRecommendedSize());
    }

    @Test
    @DisplayName("INV-R04 키가 두 사이즈의 권장 키 범위 사이 빈틈에 끼면 2cm 안의 두 사이즈가 모두 후보다")
    void candidatesInGapBetweenSizes() {
        ProductSizes sizes = ProductSizes.of(false, List.of(
            SizeRange.of("110", 105, 114),
            SizeRange.of("120", 116, 125)));

        SizeRecommendation result = recommender.recommend(childSizeInputWithHeight(115), sizes, TODAY);

        assertThat(result.getCandidateSizes()).containsExactly("110", "120");
    }

    @Test
    @DisplayName("INV-R04 같은 사이즈의 옵션이 여러 개여도 후보에는 그 사이즈가 한 번만 크기 순으로 들어간다")
    void candidatesDistinctBySize() {
        ProductSizes sizes = ProductSizes.of(false, List.of(
            SizeRange.of("120", 115, 125),
            SizeRange.of("110", 105, 115),
            SizeRange.of("110", 105, 115),
            SizeRange.of("120", 115, 125)));

        SizeRecommendation result = recommender.recommend(childSizeInputWithHeight(115), sizes, TODAY);

        assertThat(result.getCandidateSizes()).containsExactly("110", "120");
    }
}
