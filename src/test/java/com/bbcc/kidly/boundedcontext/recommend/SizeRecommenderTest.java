package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.TODAY;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.childSizeInputWithHeight;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.productA;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.recommender;
import static org.assertj.core.api.Assertions.assertThat;

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
}
