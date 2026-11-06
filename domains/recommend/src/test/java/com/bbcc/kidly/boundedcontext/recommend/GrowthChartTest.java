package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.shared.childprofile.domain.Gender.MALE;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.domain.size.BmiForAgeRow;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.GrowthChart;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.InfantWeightForAgeRow;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.InfantWeightForLengthRow;
import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.childprofile.domain.Gender;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GrowthChartTest {

    /**
     * 손으로 만든 작은 기준표다. 키 100cm면 체질량지수가 몸무게와 같고, 키 50cm면 몸무게의 4배라서 경계값을 오차 없이 넣을 수 있다.
     * 키가 100cm가 아닌 줄이 있어야 체질량지수 식이 키를 잘못 쓴 경우도 잡힌다.
     * 228개월 행은 표에 행이 있어도 228개월부터는 판정하지 않는지 보려고 넣었다.
     */
    private static final GrowthChart CHART = GrowthChart.of(
        List.of(
            new BmiForAgeRow(MALE, 24, 14.0, 18.0),
            new BmiForAgeRow(MALE, 72, 14.0, 18.0),
            new BmiForAgeRow(MALE, 227, 14.0, 18.0),
            new BmiForAgeRow(MALE, 228, 14.0, 18.0)),
        List.of(
            new InfantWeightForAgeRow(MALE, 12, 8.0),
            new InfantWeightForAgeRow(MALE, 23, 9.0)),
        List.of(
            new InfantWeightForLengthRow(MALE, 60.0, 7.0),
            new InfantWeightForLengthRow(MALE, 75.0, 11.0),
            new InfantWeightForLengthRow(MALE, 80.0, 11.0),
            new InfantWeightForLengthRow(MALE, 80.5, 12.0),
            new InfantWeightForLengthRow(MALE, 100.0, 17.0)));

    @ParameterizedTest(name = "72개월, 키 {0}cm, 몸무게 {1}kg → {2}")
    @CsvSource({
        "100.0, 13.9, SLIM",
        "100.0, 14.0, NORMAL",
        "100.0, 17.9, NORMAL",
        "100.0, 18.0, CHUNKY",
        "50.0, 3.4, SLIM",
        "50.0, 3.5, NORMAL",
        "50.0, 4.4, NORMAL",
        "50.0, 4.5, CHUNKY",
    })
    @DisplayName("규칙 4 24개월 이상은 체질량지수가 5백분위 미만이면 마름, 85백분위 이상이면 통통, 그 사이는 보통이다")
    void judgeByBmiFrom24Months(double heightCm, double weightKg, BodyType expected) {
        assertThat(CHART.judge(MALE, 72, heightCm, weightKg)).contains(expected);
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

    @ParameterizedTest(name = "{0}개월, 키 100cm, 몸무게 13kg → {1}")
    @CsvSource({
        "23, NORMAL",
        "24, SLIM",
        "227, SLIM",
    })
    @DisplayName("규칙 14-2 23개월까지는 영아 기준, 24개월부터 227개월까지는 그 개월의 체질량지수 기준으로 판정한다")
    void chooseCriteriaByMonths(long months, BodyType expected) {
        assertThat(CHART.judge(MALE, months, 100.0, 13.0)).contains(expected);
    }

    @ParameterizedTest(name = "12개월, 키 {0}cm, 몸무게 11.5kg → {1}")
    @CsvSource({
        "80.2, CHUNKY",
        "80.25, NORMAL",
        "80.3, NORMAL",
    })
    @DisplayName("규칙 14-2 24개월 미만은 키를 0.5cm 단위로 반올림해 신장별 체중 행을 찾는다")
    void roundInfantLengthToHalfCentimeter(double heightCm, BodyType expected) {
        assertThat(CHART.judge(MALE, 12, heightCm, 11.5)).contains(expected);
    }

    @ParameterizedTest(name = "{0}, {1}개월, 키 {2}cm")
    @CsvSource({
        "MALE, 228, 100.0",
        "MALE, -1, 100.0",
        "MALE, 12, 59.5",
        "MALE, 12, 100.5",
        "FEMALE, 72, 100.0",
    })
    @DisplayName("규칙 14-3 228개월부터, 또는 기준표에 없는 개월 · 누운키 · 성별이면 판정하지 않는다")
    void noJudgementOutsideChart(Gender gender, long months, double heightCm) {
        assertThat(CHART.judge(gender, months, heightCm, 13.0)).isEmpty();
    }
}
