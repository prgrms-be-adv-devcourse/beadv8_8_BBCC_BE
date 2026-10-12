package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.domain.AgeGroup;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AgeGroupTest {

    @ParameterizedTest(name = "{0}, 키 {1}cm → {2}cm")
    @CsvSource({
        "MONTHS_0_23, 80.2, 80.0",
        "MONTHS_0_23, 80.3, 80.5",
        "MONTHS_24_35, 114.7, 114.5",
        "MONTHS_24_35, 114.8, 115.0",
        "YEARS_3_18, 114.4, 114.0",
        "YEARS_3_18, 114.5, 115.0",
    })
    @DisplayName("체형표에서 찾을 키는 3세 미만이면 0.5cm, 3세 이상이면 1cm 단위로 반올림한다")
    void roundHeight(AgeGroup ageGroup, double heightCm, double expectedHeightCm) {
        assertThat(ageGroup.roundHeight(heightCm)).isEqualTo(expectedHeightCm);
    }
}
