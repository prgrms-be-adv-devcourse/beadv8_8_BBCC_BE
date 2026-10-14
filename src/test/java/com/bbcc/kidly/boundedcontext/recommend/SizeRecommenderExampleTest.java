package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.TODAY;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.childSizeInputWith;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.productA;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.domain.size.Fit;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.SizeNotice;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.SizeRecommendation;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.SizeRecommender;
import com.bbcc.kidly.boundedcontext.recommend.out.ClasspathGrowthChart;
import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.childprofile.domain.PreferredFit;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SizeRecommenderExampleTest {

    private static final SizeRecommender RECOMMENDER = new SizeRecommender(new ClasspathGrowthChart());

    @ParameterizedTest(name = "몸무게 {0}kg, 입력 체형 {1}, 선호 핏 {2} → {3}, {4}, 안내 [{5}]")
    @CsvSource({
        "20.0, NORMAL, LOOSE, 120, ROOMY, ''",
        "23.0, NORMAL, LOOSE, 120, PROPER, BODY_TYPE_ADJUSTED",
        "18.0, NORMAL, LOOSE, 110, PROPER, BODY_TYPE_ADJUSTED PREFERRED_FIT_NOT_APPLIED",
        "23.0, NORMAL, SLIM, 120, PROPER, BODY_TYPE_ADJUSTED PREFERRED_FIT_NOT_APPLIED",
        "23.0, SLIM, LOOSE, 120, ROOMY, HEIGHT_ONLY",
        "23.0, , LOOSE, 120, PROPER, ''",
    })
    @DisplayName("기획서 결정 기록의 예시대로 추천한다 (상품 A, 키 115cm, 3 ~ 18세 남자, 실제 성장도표)")
    void recommendAsDecisionLogExample(
        double weightKg,
        BodyType inputBodyType,
        PreferredFit preferredFit,
        String expectedSize,
        Fit expectedFit,
        String expectedNotices) {
        SizeRecommendation result = RECOMMENDER.recommend(
            childSizeInputWith(115.0, weightKg, inputBodyType, preferredFit), productA(), TODAY);

        assertThat(result.getRecommendedSize()).isEqualTo(expectedSize);
        assertThat(result.getFit()).isEqualTo(expectedFit);
        assertThat(result.getNotices()).isEqualTo(noticesOf(expectedNotices));
    }

    private List<SizeNotice> noticesOf(String names) {
        return Arrays.stream(names.split(" "))
            .filter(name -> !name.isBlank())
            .map(SizeNotice::valueOf)
            .toList();
    }
}
