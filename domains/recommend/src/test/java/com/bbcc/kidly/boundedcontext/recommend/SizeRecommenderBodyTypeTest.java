package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.TODAY;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.childSizeInputWith;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.recommender;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.childprofile.domain.PreferredFit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SizeRecommenderBodyTypeTest {

    @ParameterizedTest(name = "몸무게 {0}kg, 입력 체형 {1} → 사이즈를 고를 때 쓰는 체형 {2}")
    @CsvSource({
        "30.0, NORMAL, CHUNKY",
        "18.0, NORMAL, SLIM",
        "20.0, CHUNKY, NORMAL",
        "30.0, , CHUNKY",
        "30.0, SLIM, ",
        "18.0, CHUNKY, ",
    })
    @DisplayName("규칙 4 사이즈를 고를 때 쓰는 체형은 판정 체형이고, 입력 체형과 두 단계 다르면 체형을 쓰지 않아 null이다")
    void decidedBodyType(double weightKg, BodyType inputBodyType, BodyType expected) {
        BodyType decided = recommender().decidedBodyType(
            childSizeInputWith(115.0, weightKg, inputBodyType, PreferredFit.LOOSE), TODAY);

        assertThat(decided).isEqualTo(expected);
    }
}
