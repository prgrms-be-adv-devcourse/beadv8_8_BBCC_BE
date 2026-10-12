package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.domain.AgeGroup;
import com.bbcc.kidly.boundedcontext.recommend.domain.WeightPercentiles;
import com.bbcc.kidly.boundedcontext.recommend.out.ClasspathGrowthChart;
import com.bbcc.kidly.shared.member.domain.Gender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ClasspathGrowthChartTest {

    private static final ClasspathGrowthChart GROWTH_CHART = new ClasspathGrowthChart();

    @ParameterizedTest(name = "{0}, {1}, 키 {2}cm → {3}kg / {4}kg")
    @CsvSource({
        "MONTHS_0_23, MALE, 45.0, 2.2, 2.7",
        "MONTHS_0_23, FEMALE, 80.5, 9.3, 11.2",
        "MONTHS_24_35, MALE, 115.0, 18.6, 22.4",
        "YEARS_3_18, MALE, 115.0, 19.1, 22.7",
        "YEARS_3_18, FEMALE, 115.0, 18.7, 22.6",
        "YEARS_3_18, MALE, 90.0, 12.2, 14.2",
        "YEARS_3_18, MALE, 186.0, 65.7, 83.9",
        "YEARS_3_18, FEMALE, 172.0, 54.5, 69.7",
    })
    @DisplayName("성장도표 CSV에서 연령 구간, 성별, 키로 15백분위와 85백분위 몸무게를 찾는다")
    void findPercentiles(
        AgeGroup ageGroup, Gender gender, double heightCm, double percentile15Kg, double percentile85Kg) {
        assertThat(GROWTH_CHART.find(ageGroup, gender, heightCm))
            .contains(WeightPercentiles.of(percentile15Kg, percentile85Kg));
    }

    @ParameterizedTest(name = "{0}, {1}, 키 {2}cm")
    @CsvSource({
        "YEARS_3_18, MALE, 89.0",
        "YEARS_3_18, MALE, 187.0",
        "YEARS_3_18, FEMALE, 173.0",
        "MONTHS_0_23, MALE, 110.5",
        "YEARS_3_18, MALE, 115.5",
    })
    @DisplayName("표에 없는 키는 빈 값을 준다 (표의 끝 밖, 표의 키 간격에 맞지 않는 키)")
    void emptyWhenHeightNotInChart(AgeGroup ageGroup, Gender gender, double heightCm) {
        assertThat(GROWTH_CHART.find(ageGroup, gender, heightCm)).isEmpty();
    }
}
