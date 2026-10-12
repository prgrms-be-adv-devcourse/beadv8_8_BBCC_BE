package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.TODAY;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.childSizeInputWithHeight;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.productA;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.recommender;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.domain.Fit;
import com.bbcc.kidly.boundedcontext.recommend.domain.SizeNotice;
import com.bbcc.kidly.boundedcontext.recommend.domain.SizeRecommendation;
import org.junit.jupiter.api.DisplayName;
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

    private SizeRecommendation recommendForHeight(double heightCm) {
        return recommender().recommend(childSizeInputWithHeight(heightCm), productA(), TODAY);
    }
}
