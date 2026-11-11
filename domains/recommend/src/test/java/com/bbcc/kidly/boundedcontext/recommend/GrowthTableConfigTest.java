package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.domain.growth.GrowthVelocityTable;
import com.bbcc.kidly.boundedcontext.recommend.out.growth.GrowthTableConfig;
import com.bbcc.kidly.shared.childprofile.domain.Gender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GrowthTableConfigTest {

    private static final GrowthVelocityTable GROWTH_VELOCITY_TABLE = new GrowthTableConfig().growthVelocityTable();

    @ParameterizedTest(name = "{0}, {1}개월 → {2}cm/년")
    @CsvSource({
        "MALE, 0, 20.00",
        "FEMALE, 72, 6.00",
        "MALE, 131, 6.29",
        "MALE, 132, 8.73",
        "FEMALE, 179, 0.92",
    })
    @DisplayName("GROW-02 한국아동패널 CSV에서 만 나이 · 성별의 1년 키 성장량 중앙값을 읽는다")
    void yearlyCmByAgeAndGender(Gender gender, long months, double expectedCmPerYear) {
        assertThat(GROWTH_VELOCITY_TABLE.yearlyCm(gender, months)).isEqualTo(expectedCmPerYear);
    }
}
