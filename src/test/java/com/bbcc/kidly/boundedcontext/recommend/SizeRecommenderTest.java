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
import org.junit.jupiter.api.Test;

class SizeRecommenderTest {

    @Test
    @DisplayName("키가 한 사이즈의 권장 키 범위에만 들면 그 사이즈를 추천한다")
    void singleCandidateInRange() {
        SizeRecommendation result = recommender().recommend(childSizeInputWithHeight(120.0), productA(), TODAY);

        assertThat(result.getRecommendedSize()).isEqualTo("120");
    }

    @Test
    @DisplayName("키가 어느 범위에도 들지 않으면 범위에서 2cm 이내인 사이즈를 추천한다")
    void candidateWithinTwoCentimetersOutOfRange() {
        SizeRecommendation result = recommender().recommend(childSizeInputWithHeight(104.0), productA(), TODAY);

        assertThat(result.getRecommendedSize()).isEqualTo("110");
    }

    @Test
    @DisplayName("키가 가장 큰 범위보다 2cm 이내로 크면 그 사이즈를 추천한다")
    void candidateWithinTwoCentimetersAboveRange() {
        SizeRecommendation result = recommender().recommend(childSizeInputWithHeight(126.0), productA(), TODAY);

        assertThat(result.getRecommendedSize()).isEqualTo("120");
    }

    @Test
    @DisplayName("범위에서 정확히 2cm 떨어진 키도 후보로 본다")
    void candidateExactlyTwoCentimetersOutOfRange() {
        SizeRecommendation result = recommender().recommend(childSizeInputWithHeight(103.0), productA(), TODAY);

        assertThat(result.getRecommendedSize()).isEqualTo("110");
    }

    @Test
    @DisplayName("모든 범위에서 2cm보다 멀면 맞는 사이즈가 없다")
    void noMatchingSizeBeyondTwoCentimeters() {
        SizeRecommendation result = recommender().recommend(childSizeInputWithHeight(102.9), productA(), TODAY);

        assertThat(result.getRecommendedSize()).isNull();
        assertThat(result.getNotices()).containsExactly(SizeNotice.NO_MATCHING_SIZE);
    }

    @Test
    @DisplayName("범위보다 작은 범위 밖 후보는 여유 있음이다")
    void roomyWhenBelowRange() {
        SizeRecommendation result = recommender().recommend(childSizeInputWithHeight(104.0), productA(), TODAY);

        assertThat(result.getFit()).isEqualTo(Fit.ROOMY);
    }
}
