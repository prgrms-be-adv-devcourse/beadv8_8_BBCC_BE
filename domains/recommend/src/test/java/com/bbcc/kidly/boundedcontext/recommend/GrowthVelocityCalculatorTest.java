package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.CHILD_1_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.bbcc.kidly.boundedcontext.recommend.domain.growth.GrowthVelocity;
import com.bbcc.kidly.boundedcontext.recommend.domain.growth.GrowthVelocityCalculator;
import com.bbcc.kidly.boundedcontext.recommend.domain.growth.RecommendGrowthRecord;
import com.bbcc.kidly.boundedcontext.recommend.out.growth.GrowthTableConfig;
import com.bbcc.kidly.shared.childprofile.domain.Gender;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class GrowthVelocityCalculatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 12);
    // 오늘 만 6세(78개월)인 여자아이다. 표의 만 6 ~ 7세 여 중앙값은 6.00cm/년이라 한 달 0.5cm다
    private static final LocalDate BIRTH_DATE_AGE_6 = LocalDate.of(2020, 5, 1);
    private static final LocalDate BIRTH_DATE_AGE_14 = LocalDate.of(2012, 5, 1);
    private static final AtomicLong RECORD_ID = new AtomicLong(1);

    private final GrowthVelocityCalculator calculator =
        new GrowthVelocityCalculator(new GrowthTableConfig().growthVelocityTable());

    static Stream<Arguments> recordsTooFewOrTooShort() {
        return Stream.of(
            Arguments.of(Named.of("기록 0개", List.of())),
            Arguments.of(Named.of("기록 1개", List.of(record(TODAY.minusMonths(2), "115.0")))),
            Arguments.of(Named.of("기록 2개, 기간 20일", List.of(
                record(TODAY.minusDays(20), "115.0"),
                record(TODAY, "117.0")))),
            Arguments.of(Named.of("달력으로 한 달이 안 됨(9월 13일 → 10월 12일)", List.of(
                record(LocalDate.of(2026, 9, 13), "115.0"),
                record(LocalDate.of(2026, 10, 12), "115.6"))))
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("recordsTooFewOrTooShort")
    @DisplayName("GROW-02 기록이 1개 이하이거나 첫 기록과 마지막 기록이 달력으로 한 달 미만 떨어져 있으면 한국아동패널 표 값 ÷ 12를 쓰고 아이 기록 비중은 0이다")
    void tableSpeedWhenRecordsTooFewOrTooShort(List<RecommendGrowthRecord> records) {
        GrowthVelocity speed = calculator.calculate(Gender.FEMALE, BIRTH_DATE_AGE_6, records, TODAY);

        assertThat(speed.cmPerMonth()).isEqualTo(0.5);
        assertThat(speed.tableCmPerMonth()).isEqualTo(0.5);
        assertThat(speed.childShare()).isEqualTo(0.0);
        assertThat(speed.childCmPerMonth()).isNull();
        assertThat(speed.tableSource()).isEqualTo("한국아동패널");
    }

    static Stream<Arguments> recordsLongEnough() {
        return Stream.of(
            Arguments.of(Named.of("기록 2개, 91일, 115 → 118cm", List.of(
                record(TODAY.minusDays(91), "115.0"),
                record(TODAY, "118.0"))), 0.235069, 0.618342),
            Arguments.of(Named.of("기록 2개, 182일, 115 → 121cm", List.of(
                record(TODAY.minusDays(182), "115.0"),
                record(TODAY, "121.0"))), 0.551415, 0.777601),
            Arguments.of(Named.of("매달 기록 12개, 330일, 한 달 0.6cm씩", IntStream.range(0, 12)
                .mapToObj(i -> record(TODAY.minusDays(30L * i), String.valueOf(121.0 - 0.6 * i)))
                .toList()), 0.905232, 0.598444),
            Arguments.of(Named.of("달력으로 한 달(9월 12일 → 10월 12일, 30일)", List.of(
                record(LocalDate.of(2026, 9, 12), "115.0"),
                record(LocalDate.of(2026, 10, 12), "115.6"))), 0.032320, 0.503515)
        );
    }

    // 기대값은 아이 기록 비중 = 표 오차² ÷ (표 오차² + 아이 기록 오차²)을 손으로 계산한 값이다.
    // 표 오차는 만 6세 여 표준편차 2.36cm/년 ÷ 12, 아이 기록 오차는 측정 오차 0.75cm ÷ √(Σ(기록 시점 − 평균 시점)²)(개월)다.
    @ParameterizedTest(name = "{0}")
    @MethodSource("recordsLongEnough")
    @DisplayName("GROW-03 첫 기록과 마지막 기록이 달력으로 한 달 이상 떨어져 있으면 아이 기록 기울기와 표 값을 오차가 작은 쪽에 비중을 두어 섞는다")
    void blendChildSlopeAndTableByError(List<RecommendGrowthRecord> records, double expectedChildShare,
        double expectedCmPerMonth) {
        GrowthVelocity speed = calculator.calculate(Gender.FEMALE, BIRTH_DATE_AGE_6, records, TODAY);

        assertThat(speed.childShare()).isCloseTo(expectedChildShare, within(0.000001));
        assertThat(speed.cmPerMonth()).isCloseTo(expectedCmPerMonth, within(0.000001));
    }

    // 처음과 끝 기록만 쓰면 5cm ÷ 182일 = 0.836cm/월이지만, 네 기록 모두로 구한 최소제곱 기울기는 0.890210cm/월이다
    @ParameterizedTest(name = "{0}")
    @MethodSource("unevenRecords")
    @DisplayName("GROW-03 아이 기록 기울기는 처음과 끝 기록이 아니라 모든 기록의 최소제곱 기울기다")
    void childSlopeIsLeastSquares(List<RecommendGrowthRecord> records, double expectedChildCmPerMonth) {
        GrowthVelocity speed = calculator.calculate(Gender.FEMALE, BIRTH_DATE_AGE_6, records, TODAY);

        assertThat(speed.childCmPerMonth()).isCloseTo(expectedChildCmPerMonth, within(0.000001));
    }

    static Stream<Arguments> unevenRecords() {
        return Stream.of(
            Arguments.of(Named.of("기록 4개, 간격이 고르지 않음", List.of(
                record(TODAY.minusDays(182), "115.0"),
                record(TODAY.minusDays(150), "115.5"),
                record(TODAY.minusDays(30), "119.5"),
                record(TODAY, "120.0"))), 0.890210)
        );
    }

    static Stream<Arguments> blendedOutOfRange() {
        return Stream.of(
            Arguments.of(Named.of("182일에 15cm 큼, 섞은 값 1.607564", List.of(
                record(TODAY.minusDays(182), "115.0"),
                record(TODAY, "130.0"))), 0.893333),
            Arguments.of(Named.of("182일에 4cm 줄어듦, 섞은 값 -0.144580", List.of(
                record(TODAY.minusDays(182), "120.0"),
                record(TODAY, "116.0"))), 0.0)
        );
    }

    @ParameterizedTest(name = "{0} → {1}cm/월")
    @MethodSource("blendedOutOfRange")
    @DisplayName("GROW-04 섞은 결과가 0 미만이면 0, 표의 중앙값 + 2 × 표준편차(만 6세 여 10.72cm/년 ÷ 12)를 넘으면 그 값으로 맞춘다")
    void clampBlendedSpeed(List<RecommendGrowthRecord> records, double expectedCmPerMonth) {
        GrowthVelocity speed = calculator.calculate(Gender.FEMALE, BIRTH_DATE_AGE_6, records, TODAY);

        assertThat(speed.cmPerMonth()).isCloseTo(expectedCmPerMonth, within(0.000001));
    }

    // 만 14 ~ 15세 여는 중앙값 0.92 · 표준편차 1.83cm/년이라, 표 값 2배(0.153cm/월)로 자르면 늦게 크는 아이의 실제 속도가 잘린다
    @ParameterizedTest(name = "{0} → {1}cm/월")
    @MethodSource("lateGrowingTeen")
    @DisplayName("GROW-04 만 14세 여는 표 값의 2배를 넘어도 중앙값 + 2 × 표준편차(0.381667cm/월)까지는 그대로 쓴다")
    void doNotCutLateGrowingTeen(List<RecommendGrowthRecord> records, double expectedCmPerMonth) {
        GrowthVelocity speed = calculator.calculate(Gender.FEMALE, BIRTH_DATE_AGE_14, records, TODAY);

        assertThat(speed.cmPerMonth()).isCloseTo(expectedCmPerMonth, within(0.000001));
    }

    static Stream<Arguments> lateGrowingTeen() {
        return Stream.of(
            Arguments.of(Named.of("182일에 150 → 153cm", List.of(
                record(TODAY.minusDays(182), "150.0"),
                record(TODAY, "153.0"))), 0.257311)
        );
    }

    static Stream<Arguments> recentRecordsEnough() {
        return Stream.of(
            Arguments.of(Named.of("최근 3개월 안 기록 2개(60일 · 오늘), 300일 전 기록은 뺌", List.of(
                record(TODAY.minusDays(300), "113.0"),
                record(TODAY.minusDays(60), "116.0"),
                record(TODAY, "117.6"))), 0.811667),
            Arguments.of(Named.of("최근 3개월 안 기록 3개, 최소제곱", List.of(
                record(TODAY.minusDays(200), "113.0"),
                record(TODAY.minusDays(80), "115.0"),
                record(TODAY.minusDays(40), "115.6"),
                record(TODAY, "116.4"))), 0.532656),
            Arguments.of(Named.of("3개월 전 날짜(8월 12일) 기록은 넣음", List.of(
                record(TODAY.minusMonths(3), "115.0"),
                record(TODAY, "117.0"))), 0.661685),
            Arguments.of(Named.of("45일에 3cm 큼, 2.029167 → 상한", List.of(
                record(TODAY.minusDays(45), "110.0"),
                record(TODAY, "113.0"))), 0.893333)
        );
    }

    @ParameterizedTest(name = "{0} → {1}cm/월")
    @MethodSource("recentRecordsEnough")
    @DisplayName("GROW-05 최근 3개월 안에 기록이 2개 이상이고 그 기록들이 달력으로 한 달 이상 떨어져 있으면 그 기록만의 최소제곱 기울기를 표와 섞지 않고 전체 속도와 같은 범위로 맞춰 최근 속도로 쓴다")
    void recentSpeedFromRecentRecords(List<RecommendGrowthRecord> records, double expectedRecentCmPerMonth) {
        GrowthVelocity speed = calculator.calculate(Gender.FEMALE, BIRTH_DATE_AGE_6, records, TODAY);

        assertThat(speed.recentCmPerMonth()).isCloseTo(expectedRecentCmPerMonth, within(0.000001));
    }

    static Stream<Arguments> recentRecordsNotEnough() {
        return Stream.of(
            Arguments.of(Named.of("최근 3개월 안 기록 1개", List.of(
                record(TODAY.minusDays(200), "113.0"),
                record(TODAY.minusDays(30), "116.0")))),
            Arguments.of(Named.of("최근 3개월 안 기록 2개, 기간 20일", List.of(
                record(TODAY.minusDays(200), "113.0"),
                record(TODAY.minusDays(20), "116.0"),
                record(TODAY, "116.5")))),
            Arguments.of(Named.of("3개월 전 날짜 하루 전(8월 11일) 기록은 뺌", List.of(
                record(TODAY.minusMonths(3).minusDays(1), "115.0"),
                record(TODAY, "117.0"))))
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("recentRecordsNotEnough")
    @DisplayName("GROW-05 최근 3개월 안 기록이 1개 이하이거나 그 기간이 1개월 미만이면 최근 속도를 표로 메우지 않고 null로 둔다")
    void noRecentSpeedWhenRecentRecordsNotEnough(List<RecommendGrowthRecord> records) {
        GrowthVelocity speed = calculator.calculate(Gender.FEMALE, BIRTH_DATE_AGE_6, records, TODAY);

        assertThat(speed.recentCmPerMonth()).isNull();
    }

    static Stream<Arguments> recordsAroundTwelveMonths() {
        return Stream.of(
            Arguments.of(Named.of("12개월 전 날짜(작년 11월 12일) 기록은 넣음", List.of(
                record(TODAY.minusMonths(12), "115.0"),
                record(TODAY.minusDays(182), "118.0"),
                record(TODAY, "121.0"))), 3, 11.991786, 0.831763, 0.500284),
            Arguments.of(Named.of("12개월 전 날짜 하루 전 기록은 뺌", List.of(
                record(TODAY.minusMonths(12).minusDays(1), "105.0"),
                record(TODAY.minusDays(182), "115.0"),
                record(TODAY, "121.0"))), 2, 5.979466, 0.551415, 0.777601),
            Arguments.of(Named.of("오늘보다 뒤 날짜 기록은 뺌", List.of(
                record(TODAY.minusDays(182), "115.0"),
                record(TODAY, "121.0"),
                record(TODAY.plusDays(30), "200.0"))), 2, 5.979466, 0.551415, 0.777601)
        );
    }

    // 사춘기 앞뒤로 성장 속도가 해마다 크게 바뀌어, 몇 년 치 기록으로 그은 직선은 지금 속도와 다르다
    @ParameterizedTest(name = "{0}")
    @MethodSource("recordsAroundTwelveMonths")
    @DisplayName("GROW-03 성장 속도는 12개월 전 날짜부터 오늘까지의 기록만으로 계산하고, 기록 기간은 첫 기록과 마지막 기록 사이 개월 수다")
    void useRecordsWithinTwelveMonths(List<RecommendGrowthRecord> records, int expectedRecordCount,
        double expectedRecordMonths, double expectedChildShare, double expectedCmPerMonth) {
        GrowthVelocity speed = calculator.calculate(Gender.FEMALE, BIRTH_DATE_AGE_6, records, TODAY);

        assertThat(speed.recordCount()).isEqualTo(expectedRecordCount);
        assertThat(speed.recordMonths()).isCloseTo(expectedRecordMonths, within(0.000001));
        assertThat(speed.childShare()).isCloseTo(expectedChildShare, within(0.000001));
        assertThat(speed.cmPerMonth()).isCloseTo(expectedCmPerMonth, within(0.000001));
    }

    static Stream<Arguments> nullInputs() {
        List<RecommendGrowthRecord> noRecords = List.of();
        return Stream.of(
            Arguments.of(Named.of("성별", (Runnable) () -> new GrowthVelocityCalculator(
                new GrowthTableConfig().growthVelocityTable()).calculate(null, BIRTH_DATE_AGE_6, noRecords, TODAY)),
                "gender는 필수입니다"),
            Arguments.of(Named.of("생년월일", (Runnable) () -> new GrowthVelocityCalculator(
                new GrowthTableConfig().growthVelocityTable()).calculate(Gender.FEMALE, null, noRecords, TODAY)),
                "birthDate는 필수입니다"),
            Arguments.of(Named.of("기록 목록", (Runnable) () -> new GrowthVelocityCalculator(
                new GrowthTableConfig().growthVelocityTable()).calculate(Gender.FEMALE, BIRTH_DATE_AGE_6, null, TODAY)),
                "records는 필수입니다"),
            Arguments.of(Named.of("오늘 날짜", (Runnable) () -> new GrowthVelocityCalculator(
                new GrowthTableConfig().growthVelocityTable()).calculate(Gender.FEMALE, BIRTH_DATE_AGE_6, noRecords, null)),
                "today는 필수입니다")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("nullInputs")
    @DisplayName("GROW-01 성장 속도 계산에 필요한 값이 null이면 계산하지 않는다")
    void rejectNullInputs(Runnable calculation, String expectedMessage) {
        assertThatThrownBy(calculation::run)
            .isInstanceOf(NullPointerException.class)
            .hasMessage(expectedMessage);
    }

    private static RecommendGrowthRecord record(LocalDate recordedOn, String heightCm) {
        return RecommendGrowthRecord.create(new RecommendGrowthRecord.RecordInfo(
            RECORD_ID.getAndIncrement(), CHILD_1_ID, new BigDecimal(heightCm), null, recordedOn.atTime(9, 0)));
    }
}
