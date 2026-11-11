package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.domain.growth.GirthPart;
import com.bbcc.kidly.boundedcontext.recommend.domain.growth.GirthRatioTable;
import com.bbcc.kidly.boundedcontext.recommend.domain.growth.GrowthVelocityTable;
import com.bbcc.kidly.boundedcontext.recommend.out.growth.GrowthTableConfig;
import com.bbcc.kidly.shared.childprofile.domain.Gender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GrowthTableConfigTest {

    private static final GrowthVelocityTable GROWTH_VELOCITY_TABLE = new GrowthTableConfig().growthVelocityTable();
    private static final GirthRatioTable GIRTH_RATIO_TABLE = new GrowthTableConfig().girthRatioTable();

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

    @ParameterizedTest(name = "{0}, {1}개월 → {2}cm/년")
    @CsvSource({
        "MALE, 180, 2.77",
        "FEMALE, 216, 0.92",
    })
    @DisplayName("GROW-02 만 15세 이상이면 마지막 구간(만 14 ~ 15세)의 1년 키 성장량을 쓴다")
    void yearlyCmOfLastAgeFromFifteen(Gender gender, long months, double expectedCmPerYear) {
        assertThat(GROWTH_VELOCITY_TABLE.yearlyCm(gender, months)).isEqualTo(expectedCmPerYear);
    }

    @ParameterizedTest(name = "{0}, {1}개월 → 표준편차 {2}cm/년")
    @CsvSource({
        "MALE, 0, 7.44",
        "FEMALE, 72, 2.36",
        "MALE, 179, 2.77",
        "FEMALE, 216, 1.83",
    })
    @DisplayName("GROW-03 한국아동패널 CSV에서 만 나이 · 성별의 1년 키 성장량 표준편차를 읽고, 만 15세 이상이면 마지막 구간 값을 쓴다")
    void yearlySdCmByAgeAndGender(Gender gender, long months, double expectedSdCmPerYear) {
        assertThat(GROWTH_VELOCITY_TABLE.yearlySdCm(gender, months)).isEqualTo(expectedSdCmPerYear);
    }

    @ParameterizedTest(name = "{0}, {1}개월, {2} → 키 1cm당 {3}cm")
    @CsvSource({
        "FEMALE, 0, CHEST, 0.30",
        "MALE, 83, WAIST, 0.23",
        "MALE, 84, WAIST, 0.42",
        "FEMALE, 143, HIP, 0.63",
        "FEMALE, 144, HIP, 1.07",
        "MALE, 144, CHEST, 0.53",
        "MALE, 240, HIP, 0.48",
    })
    @DisplayName("WEAR-02 사이즈코리아 CSV에서 나이 구간(만 0 ~ 6세, 7 ~ 11세, 12세 이상) · 성별 · 부위의 키 1cm당 둘레 증가량을 읽는다")
    void girthRatioByAgeGenderAndPart(Gender gender, long months, GirthPart part, double expectedRatio) {
        assertThat(GIRTH_RATIO_TABLE.ratio(gender, months, part)).isEqualTo(expectedRatio);
    }
}
