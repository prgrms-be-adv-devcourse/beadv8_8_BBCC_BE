package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.CHILD_1_ID;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.recommend.domain.growth.RecommendGrowthRecord;
import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class RecommendGrowthRecordTest {

    private static final Long GROWTH_RECORD_ID = 501L;

    private static final BigDecimal HEIGHT_CM = new BigDecimal("115.5");

    private static final LocalDateTime RECORDED_AT = LocalDateTime.of(2026, 11, 1, 9, 0);

    @ParameterizedTest(name = "{0}")
    @MethodSource("creationsWithNull")
    @DisplayName("성장 기록 값에 필수 값이 없으면 기록을 만들지 않는다")
    void rejectCreationWithNull(ThrowingCallable creation) {
        assertThatThrownBy(creation)
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("필수입니다");
    }

    static Stream<Named<ThrowingCallable>> creationsWithNull() {
        return Stream.of(
            Named.of("성장 기록 값", () -> RecommendGrowthRecord.create(null)),
            Named.of("기록 ID", () -> RecommendGrowthRecord.create(new RecommendGrowthRecord.RecordInfo(
                null, CHILD_1_ID, HEIGHT_CM, BodyType.SLIM, RECORDED_AT))),
            Named.of("자녀 ID", () -> RecommendGrowthRecord.create(new RecommendGrowthRecord.RecordInfo(
                GROWTH_RECORD_ID, null, HEIGHT_CM, BodyType.SLIM, RECORDED_AT))),
            Named.of("키", () -> RecommendGrowthRecord.create(new RecommendGrowthRecord.RecordInfo(
                GROWTH_RECORD_ID, CHILD_1_ID, null, BodyType.SLIM, RECORDED_AT))),
            Named.of("기록 시각", () -> RecommendGrowthRecord.create(new RecommendGrowthRecord.RecordInfo(
                GROWTH_RECORD_ID, CHILD_1_ID, HEIGHT_CM, BodyType.SLIM, null))));
    }
}
