package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.domain.size.GrowthChart;
import com.bbcc.kidly.boundedcontext.recommend.out.size.GrowthChartConfig;
import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.childprofile.domain.Gender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GrowthChartConfigTest {

    private static final GrowthChart GROWTH_CHART = new GrowthChartConfig().growthChart();

    @ParameterizedTest(name = "{0}, {1}개월, 키 100cm, 몸무게 {2}kg → {3}")
    @CsvSource({
        "MALE, 24, 14.1, SLIM",
        "MALE, 24, 14.2, NORMAL",
        "MALE, 24, 17.3, NORMAL",
        "MALE, 24, 17.4, CHUNKY",
        "MALE, 72, 13.8, SLIM",
        "MALE, 72, 13.9, NORMAL",
        "MALE, 72, 17.5, NORMAL",
        "MALE, 72, 17.6, CHUNKY",
        "MALE, 96, 14.1, SLIM",
        "MALE, 96, 14.2, NORMAL",
        "MALE, 96, 19.1, NORMAL",
        "MALE, 96, 19.2, CHUNKY",
        "FEMALE, 72, 13.6, SLIM",
        "FEMALE, 72, 13.7, NORMAL",
        "FEMALE, 72, 17.4, NORMAL",
        "FEMALE, 72, 17.5, CHUNKY",
        "FEMALE, 227, 17.6, SLIM",
        "FEMALE, 227, 17.7, NORMAL",
        "FEMALE, 227, 23.5, NORMAL",
        "FEMALE, 227, 23.6, CHUNKY",
    })
    @DisplayName("규칙 14 성장도표 CSV의 연령별 체질량지수 5백분위와 85백분위로 판정한다")
    void judgeByBmiForAgeCsv(Gender gender, long months, double weightKg, BodyType expected) {
        assertThat(GROWTH_CHART.judge(gender, months, 100.0, weightKg)).contains(expected);
    }

    @ParameterizedTest(name = "{0}, {1}개월, 키 {2}cm, 몸무게 {3}kg → {4}")
    @CsvSource({
        "MALE, 12, 75.0, 8.0, SLIM",
        "MALE, 12, 75.0, 8.1, NORMAL",
        "MALE, 12, 75.0, 10.8, NORMAL",
        "MALE, 12, 75.0, 10.9, CHUNKY",
        "MALE, 0, 45.0, 2.5, SLIM",
        "MALE, 0, 45.0, 2.9, CHUNKY",
        "FEMALE, 23, 110.0, 9.1, SLIM",
        "FEMALE, 23, 110.0, 21.4, NORMAL",
        "FEMALE, 23, 110.0, 21.5, CHUNKY",
    })
    @DisplayName("규칙 14 성장도표 CSV의 연령별 체중 5백분위와 신장별 체중 95백분위로 24개월 미만을 판정한다")
    void judgeInfantByCsv(Gender gender, long months, double heightCm, double weightKg, BodyType expected) {
        assertThat(GROWTH_CHART.judge(gender, months, heightCm, weightKg)).contains(expected);
    }

    @ParameterizedTest(name = "{0}, {1}개월, 키 {2}cm")
    @CsvSource({
        "MALE, 228, 100.0",
        "FEMALE, 228, 100.0",
        "MALE, 23, 44.5",
        "MALE, 23, 110.5",
    })
    @DisplayName("규칙 14-3 성장도표 CSV 범위 밖(228개월부터, 누운키 45cm 미만 · 110cm 초과)이면 판정하지 않는다")
    void noJudgementOutsideCsv(Gender gender, long months, double heightCm) {
        assertThat(GROWTH_CHART.judge(gender, months, heightCm, 20.0)).isEmpty();
    }
}
