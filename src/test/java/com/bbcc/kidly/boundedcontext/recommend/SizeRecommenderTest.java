package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.TODAY;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.childSizeInputWith;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.childSizeInputWithHeight;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.productA;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.recommender;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.domain.BodyType;
import com.bbcc.kidly.boundedcontext.recommend.domain.Fit;
import com.bbcc.kidly.boundedcontext.recommend.domain.PreferredFit;
import com.bbcc.kidly.boundedcontext.recommend.domain.ProductSizes;
import com.bbcc.kidly.boundedcontext.recommend.domain.SizeNotice;
import com.bbcc.kidly.boundedcontext.recommend.domain.SizeRange;
import com.bbcc.kidly.boundedcontext.recommend.domain.SizeRecommendation;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class SizeRecommenderTest {

    @ParameterizedTest(name = "키 {0}cm → {1}")
    @CsvSource({
        "120.0, 120",
        "104.0, 110",
        "103.0, 110",
        "126.0, 120",
        "127.0, 120",
        "116.0, 120",
    })
    @DisplayName("키가 범위 안에 들거나 범위에서 2cm 이내인 사이즈를 추천한다")
    void recommendedSizeByHeight(double heightCm, String expectedSize) {
        SizeRecommendation result = recommendForHeight(heightCm);

        assertThat(result.getRecommendedSize()).isEqualTo(expectedSize);
    }

    @ParameterizedTest(name = "키 {0}cm")
    @ValueSource(doubles = {102.9, 127.1})
    @DisplayName("모든 범위에서 2cm보다 멀면 맞는 사이즈가 없다")
    void noMatchingSizeBeyondTwoCentimeters(double heightCm) {
        SizeRecommendation result = recommendForHeight(heightCm);

        assertThat(result.getRecommendedSize()).isNull();
        assertThat(result.getNotices()).containsExactly(SizeNotice.NO_MATCHING_SIZE);
    }

    @ParameterizedTest(name = "키 {0}cm → {1}")
    @CsvSource({
        "104.0, ROOMY",
        "103.0, ROOMY",
        "126.0, TIGHT",
        "127.0, TIGHT",
    })
    @DisplayName("범위 밖 후보는 범위보다 작으면 여유 있음, 크면 작을 가능성 있음이다")
    void fitOutOfRange(double heightCm, Fit expectedFit) {
        SizeRecommendation result = recommendForHeight(heightCm);

        assertThat(result.getFit()).isEqualTo(expectedFit);
    }

    @ParameterizedTest(name = "키 {0}cm → {1}")
    @CsvSource({
        "116.0, ROOMY",
        "120.0, PROPER",
        "124.0, TIGHT",
        "107.0, ROOMY",
        "107.1, PROPER",
        "112.9, PROPER",
        "113.0, TIGHT",
    })
    @DisplayName("범위 안 후보는 아래쪽 끝 2cm면 여유 있음, 위쪽 끝 2cm면 작을 가능성 있음, 그 사이는 적정이다")
    void fitInRange(double heightCm, Fit expectedFit) {
        SizeRecommendation result = recommendForHeight(heightCm);

        assertThat(result.getFit()).isEqualTo(expectedFit);
    }

    @ParameterizedTest(name = "범위 {0}~{1}cm, 키 {2}cm → {3}")
    @CsvSource({
        "110, 113, 111.0, PROPER",
        "110, 113, 112.0, PROPER",
        "110, 113, 110.0, ROOMY",
        "110, 113, 113.0, TIGHT",
        "110, 111, 109.0, ROOMY",
        "110, 111, 112.0, TIGHT",
    })
    @DisplayName("범위가 4cm 이하라 두 끝 구간이 겹치는 키는 적정이고, 범위 밖은 겹침과 관계없다")
    void fitInNarrowRange(int minHeightCm, int maxHeightCm, double heightCm, Fit expectedFit) {
        ProductSizes narrowProduct = ProductSizes.of(false, List.of(SizeRange.of("110", minHeightCm, maxHeightCm)));

        SizeRecommendation result = recommender().recommend(childSizeInputWithHeight(heightCm), narrowProduct, TODAY);

        assertThat(result.getFit()).isEqualTo(expectedFit);
    }

    @ParameterizedTest(name = "몸무게 {0}kg, 체형 {1}, 선호 핏 {2} → {3}")
    @CsvSource({
        "20.0, NORMAL, LOOSE, 120",
        "20.0, NORMAL, SLIM, 110",
        "20.0, NORMAL, , 120",
        "23.0, CHUNKY, SLIM, 120",
        "18.0, SLIM, LOOSE, 110",
        "20.0, NORMAL, REGULAR, 120",
    })
    @DisplayName("키 115cm로 후보가 110과 120 둘이면 체형과 선호 핏으로 하나를 고른다")
    void chooseBetweenTwoCandidates(
        double weightKg, BodyType bodyType, PreferredFit preferredFit, String expectedSize) {
        SizeRecommendation result = recommender()
            .recommend(childSizeInputWith(115.0, weightKg, bodyType, preferredFit), productA(), TODAY);

        assertThat(result.getRecommendedSize()).isEqualTo(expectedSize);
    }

    @ParameterizedTest(name = "키 {0}cm → {1}")
    @CsvSource({
        "114.0, 110",
        "115.0, 120",
        "114.75, 120",
    })
    @DisplayName("선호 핏이 보통 핏이면 키가 범위 한가운데에 더 가까운 사이즈를 고른다")
    void regularFitChoosesSizeNearestToCenter(double heightCm, String expectedSize) {
        ProductSizes overlappingProduct = ProductSizes.of(false, List.of(
            SizeRange.of("110", 105, 116),
            SizeRange.of("120", 113, 125)));

        SizeRecommendation result = recommender().recommend(
            childSizeInputWith(heightCm, 20.0, BodyType.NORMAL, PreferredFit.REGULAR), overlappingProduct, TODAY);

        assertThat(result.getRecommendedSize()).isEqualTo(expectedSize);
    }

    @ParameterizedTest(name = "키 {0}cm, 몸무게 {1}kg, 체형 {2} → {3}")
    @CsvSource({
        "115.0, 23.0, CHUNKY, PROPER",
        "115.0, 18.0, SLIM, PROPER",
        "120.0, 23.0, CHUNKY, TIGHT",
        "120.0, 18.0, SLIM, ROOMY",
        "124.0, 23.0, CHUNKY, TIGHT",
        "116.0, 18.0, SLIM, ROOMY",
        "104.0, 23.0, CHUNKY, ROOMY",
        "126.0, 18.0, SLIM, TIGHT",
    })
    @DisplayName("범위 안 후보는 통통하면 작을 가능성 쪽, 마르면 여유 쪽으로 한 단계 옮기고 범위 밖 후보는 옮기지 않는다")
    void fitAdjustedByBodyType(double heightCm, double weightKg, BodyType bodyType, Fit expectedFit) {
        SizeRecommendation result = recommender().recommend(
            childSizeInputWith(heightCm, weightKg, bodyType, PreferredFit.LOOSE), productA(), TODAY);

        assertThat(result.getFit()).isEqualTo(expectedFit);
    }

    @Test
    @DisplayName("사이즈 무관 상품이면 사이즈와 적합도 없이 SIZE_FREE 안내만 준다")
    void sizeFreeProduct() {
        ProductSizes sizeFreeProduct = ProductSizes.of(true, List.of());

        SizeRecommendation result = recommender().recommend(childSizeInputWithHeight(115.0), sizeFreeProduct, TODAY);

        assertThat(result.getRecommendedSize()).isNull();
        assertThat(result.getFit()).isNull();
        assertThat(result.getNotices()).containsExactly(SizeNotice.SIZE_FREE);
    }

    private SizeRecommendation recommendForHeight(double heightCm) {
        return recommender().recommend(childSizeInputWithHeight(heightCm), productA(), TODAY);
    }
}
