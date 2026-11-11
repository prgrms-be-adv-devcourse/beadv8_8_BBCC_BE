package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.domain.growth.GirthPart;
import com.bbcc.kidly.boundedcontext.recommend.domain.growth.GirthRatioTable;
import com.bbcc.kidly.boundedcontext.recommend.domain.growth.GrowthVelocityTable;
import com.bbcc.kidly.boundedcontext.recommend.domain.wearpreview.BodyHeightRatioRow;
import com.bbcc.kidly.boundedcontext.recommend.domain.wearpreview.BodyHeightRatioTable;
import com.bbcc.kidly.boundedcontext.recommend.out.growth.GrowthTableConfig;
import com.bbcc.kidly.shared.childprofile.domain.Gender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class GrowthTableConfigTest {

    private static final GrowthVelocityTable GROWTH_VELOCITY_TABLE = new GrowthTableConfig().growthVelocityTable();
    private static final GirthRatioTable GIRTH_RATIO_TABLE = new GrowthTableConfig().girthRatioTable();
    private static final BodyHeightRatioTable BODY_HEIGHT_RATIO_TABLE = new GrowthTableConfig().bodyHeightRatioTable();

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

    // 자녀 정보는 생일이 오늘 이후가 될 수 없게 검증된 값이지만, 잘못 들어온 데이터에서도 계산이 멈추지 않게 한다
    @ParameterizedTest
    @ValueSource(longs = {-1, -11, -13, -30})
    @DisplayName("생일이 오늘보다 뒤라 개월 수가 음수면 성장량 표와 둘레 비율 표 모두 0세 값을 쓴다")
    void useAgeZeroForNegativeMonths(long months) {
        assertThat(GROWTH_VELOCITY_TABLE.yearlyCm(Gender.MALE, months))
            .isEqualTo(GROWTH_VELOCITY_TABLE.yearlyCm(Gender.MALE, 0));
        assertThat(GIRTH_RATIO_TABLE.ratio(Gender.MALE, months, GirthPart.CHEST))
            .isEqualTo(GIRTH_RATIO_TABLE.ratio(Gender.MALE, 0, GirthPart.CHEST));
    }

    @ParameterizedTest(name = "{0}, {1}개월 → 목뒤 {2} · 허리 {3} · 엉덩이 {4} · 팔길이 {5}")
    @CsvSource({
        "FEMALE, 72, 0.828, 0.605, 0.481, 0.326",
        "FEMALE, 12, 0.828, 0.605, 0.430, 0.297",
        "MALE, 36, 0.824, 0.604, 0.443, 0.315",
        "MALE, 131, 0.841, 0.606, 0.506, 0.337",
        "MALE, 132, 0.846, 0.610, 0.511, 0.339",
        "FEMALE, 240, 0.847, 0.615, 0.501, 0.341",
    })
    @DisplayName("PRV-01 사이즈코리아 CSV에서 만 나이 · 성별의 키 대비 높이 비율을 읽고, 만 15세 이상이면 마지막 구간 값을 쓴다")
    void bodyHeightRatioByAgeAndGender(Gender gender, long months, double neck, double waist, double hip, double arm) {
        BodyHeightRatioRow row = BODY_HEIGHT_RATIO_TABLE.ratios(gender, months);

        assertThat(row.neck()).isEqualTo(neck);
        assertThat(row.waist()).isEqualTo(waist);
        assertThat(row.hip()).isEqualTo(hip);
        assertThat(row.arm()).isEqualTo(arm);
    }
}
