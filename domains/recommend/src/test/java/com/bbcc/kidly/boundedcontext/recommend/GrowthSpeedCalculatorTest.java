package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.CHILD_1_ID;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.domain.growth.GrowthSpeed;
import com.bbcc.kidly.boundedcontext.recommend.domain.growth.GrowthSpeedCalculator;
import com.bbcc.kidly.boundedcontext.recommend.domain.growth.RecommendGrowthRecord;
import com.bbcc.kidly.boundedcontext.recommend.out.growth.GrowthTableConfig;
import com.bbcc.kidly.shared.childprofile.domain.Gender;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
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
        assertThat(speed.tableSource()).isEqualTo("한국아동패널");
    }

    private static RecommendGrowthRecord record(LocalDate recordedOn, String heightCm) {
        return RecommendGrowthRecord.create(new RecommendGrowthRecord.RecordInfo(
            RECORD_ID.getAndIncrement(), CHILD_1_ID, new BigDecimal(heightCm), null, recordedOn.atTime(9, 0)));
    }
}
