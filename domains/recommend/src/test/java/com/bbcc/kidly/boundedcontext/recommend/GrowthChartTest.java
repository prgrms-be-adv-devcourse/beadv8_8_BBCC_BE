package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.shared.childprofile.domain.Gender.MALE;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.domain.size.BmiForAgeRow;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.GrowthChart;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.InfantWeightForAgeRow;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.InfantWeightForLengthRow;
import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GrowthChartTest {

    /**
     * 손으로 만든 작은 기준표다. 키 100cm면 체질량지수가 몸무게와 같아 경계값을 그대로 넣을 수 있다.
     */
    private static final GrowthChart CHART = GrowthChart.of(
        List.of(new BmiForAgeRow(MALE, 72, 14.0, 18.0)),
        List.of(new InfantWeightForAgeRow(MALE, 12, 8.0)),
        List.of(
            new InfantWeightForLengthRow(MALE, 60.0, 7.0),
            new InfantWeightForLengthRow(MALE, 75.0, 11.0)));

    @ParameterizedTest(name = "72개월, 키 100cm, 몸무게 {0}kg → {1}")
    @CsvSource({
        "13.9, SLIM",
        "14.0, NORMAL",
        "17.9, NORMAL",
        "18.0, CHUNKY",
    })
    @DisplayName("규칙 4 24개월 이상은 체질량지수가 5백분위 미만이면 마름, 85백분위 이상이면 통통, 그 사이는 보통이다")
    void judgeByBmiFrom24Months(double weightKg, BodyType expected) {
        assertThat(CHART.judge(MALE, 72, 100.0, weightKg)).contains(expected);
    }

    @ParameterizedTest(name = "12개월, 키 {0}cm, 몸무게 {1}kg → {2}")
    @CsvSource({
        "75.0, 7.9, SLIM",
        "75.0, 8.0, NORMAL",
        "75.0, 10.9, NORMAL",
        "75.0, 11.0, CHUNKY",
        "60.0, 7.5, CHUNKY",
    })
    @DisplayName("규칙 4 24개월 미만은 연령별 체중 5백분위 미만이면 마름, 신장별 체중 95백분위 이상이면 통통이고, 둘 다 맞으면 통통이다")
    void judgeInfantByWeightForAgeAndLength(double heightCm, double weightKg, BodyType expected) {
        assertThat(CHART.judge(MALE, 12, heightCm, weightKg)).contains(expected);
    }
}
