package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.CHILD_1_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.bbcc.kidly.boundedcontext.recommend.domain.growth.GrowthSpeed;
import com.bbcc.kidly.boundedcontext.recommend.domain.growth.GrowthSpeedCalculator;
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

class GrowthSpeedCalculatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 12);
    // 오늘 만 6세(78개월)인 여자아이다. 표의 만 6 ~ 7세 여 중앙값은 6.00cm/년이라 한 달 0.5cm다
    private static final LocalDate BIRTH_DATE_AGE_6 = LocalDate.of(2020, 5, 1);
    private static final AtomicLong RECORD_ID = new AtomicLong(1);

    private final GrowthSpeedCalculator calculator =
        new GrowthSpeedCalculator(new GrowthTableConfig().growthVelocityTable());

    static Stream<Arguments> recordsTooFewOrTooShort() {
        return Stream.of(
            Arguments.of(Named.of("기록 0개", List.of())),
            Arguments.of(Named.of("기록 1개", List.of(record(TODAY.minusMonths(2), "115.0")))),
            Arguments.of(Named.of("기록 2개, 기간 20일", List.of(
                record(TODAY.minusDays(20), "115.0"),
                record(TODAY, "117.0"))))
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("recordsTooFewOrTooShort")
    @DisplayName("GROW-02 기록이 1개 이하이거나 기간이 1개월 미만이면 한국아동패널 표 값 ÷ 12를 쓰고 아이 기록 비중은 0이다")
    void tableSpeedWhenRecordsTooFewOrTooShort(List<RecommendGrowthRecord> records) {
        GrowthSpeed speed = calculator.calculate(Gender.FEMALE, BIRTH_DATE_AGE_6, records, TODAY);

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
                .toList()), 0.905232, 0.598444)
        );
    }

    // 기대값은 아이 기록 비중 = 표 오차² ÷ (표 오차² + 아이 기록 오차²)을 손으로 계산한 값이다.
    // 표 오차는 만 6세 여 표준편차 2.36cm/년 ÷ 12, 아이 기록 오차는 측정 오차 0.75cm ÷ √(Σ(기록 시점 − 평균 시점)²)(개월)다.
    @ParameterizedTest(name = "{0}")
    @MethodSource("recordsLongEnough")
    @DisplayName("GROW-03 기간이 1개월 이상이면 아이 기록 기울기와 표 값을 오차가 작은 쪽에 비중을 두어 섞는다")
    void blendChildSlopeAndTableByError(List<RecommendGrowthRecord> records, double expectedChildShare,
        double expectedCmPerMonth) {
        GrowthSpeed speed = calculator.calculate(Gender.FEMALE, BIRTH_DATE_AGE_6, records, TODAY);

        assertThat(speed.childShare()).isCloseTo(expectedChildShare, within(0.000001));
        assertThat(speed.cmPerMonth()).isCloseTo(expectedCmPerMonth, within(0.000001));
    }

    // 처음과 끝 기록만 쓰면 5cm ÷ 182일 = 0.836cm/월이지만, 네 기록 모두로 구한 최소제곱 기울기는 0.890210cm/월이다
    @ParameterizedTest(name = "{0}")
    @MethodSource("unevenRecords")
    @DisplayName("GROW-03 아이 기록 기울기는 처음과 끝 기록이 아니라 모든 기록의 최소제곱 기울기다")
    void childSlopeIsLeastSquares(List<RecommendGrowthRecord> records, double expectedChildCmPerMonth) {
        GrowthSpeed speed = calculator.calculate(Gender.FEMALE, BIRTH_DATE_AGE_6, records, TODAY);

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
                record(TODAY, "130.0"))), 1.0),
            Arguments.of(Named.of("182일에 4cm 줄어듦, 섞은 값 -0.144580", List.of(
                record(TODAY.minusDays(182), "120.0"),
                record(TODAY, "116.0"))), 0.0)
        );
    }

    @ParameterizedTest(name = "{0} → {1}cm/월")
    @MethodSource("blendedOutOfRange")
    @DisplayName("GROW-04 섞은 결과가 0 미만이면 0, 표 값의 2배를 넘으면 2배로 맞춘다")
    void clampBlendedSpeed(List<RecommendGrowthRecord> records, double expectedCmPerMonth) {
        GrowthSpeed speed = calculator.calculate(Gender.FEMALE, BIRTH_DATE_AGE_6, records, TODAY);

        assertThat(speed.cmPerMonth()).isEqualTo(expectedCmPerMonth);
    }

    static Stream<Arguments> recentRecordsEnough() {
        return Stream.of(
            Arguments.of(Named.of("최근 3개월 안 기록 2개(60일 · 오늘), 300일 전 기록은 뺌", List.of(
                record(TODAY.minusDays(300), "113.0"),
                record(TODAY.minusDays(60), "116.0"),
                record(TODAY, "117.8"))), 0.913125),
            Arguments.of(Named.of("최근 3개월 안 기록 3개, 최소제곱", List.of(
                record(TODAY.minusDays(200), "113.0"),
                record(TODAY.minusDays(80), "115.0"),
                record(TODAY.minusDays(40), "115.6"),
                record(TODAY, "116.4"))), 0.532656),
            Arguments.of(Named.of("3개월 전 날짜(8월 12일) 기록은 넣음", List.of(
                record(TODAY.minusMonths(3), "115.0"),
                record(TODAY, "117.0"))), 0.661685),
            Arguments.of(Named.of("45일에 3cm 큼, 2.029167 → 표 값 2배", List.of(
                record(TODAY.minusDays(45), "110.0"),
                record(TODAY, "113.0"))), 1.0)
        );
    }

    @ParameterizedTest(name = "{0} → {1}cm/월")
    @MethodSource("recentRecordsEnough")
    @DisplayName("GROW-05 최근 3개월 안에 기록이 2개 이상이고 그 기간이 1개월 이상이면 그 기록만의 최소제곱 기울기를 표와 섞지 않고 0 ~ 표 값 2배로 맞춰 최근 속도로 쓴다")
    void recentSpeedFromRecentRecords(List<RecommendGrowthRecord> records, double expectedRecentCmPerMonth) {
        GrowthSpeed speed = calculator.calculate(Gender.FEMALE, BIRTH_DATE_AGE_6, records, TODAY);

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
        GrowthSpeed speed = calculator.calculate(Gender.FEMALE, BIRTH_DATE_AGE_6, records, TODAY);

        assertThat(speed.recentCmPerMonth()).isNull();
    }

    private static RecommendGrowthRecord record(LocalDate recordedOn, String heightCm) {
        return RecommendGrowthRecord.create(new RecommendGrowthRecord.RecordInfo(
            RECORD_ID.getAndIncrement(), CHILD_1_ID, new BigDecimal(heightCm), null, recordedOn.atTime(9, 0)));
    }
}
