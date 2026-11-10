package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.GarmentPart;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.ReferenceGarmentErrorCode;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.ReferenceGarmentSpec;
import com.bbcc.kidly.global.error.BusinessException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ReferenceGarmentSpecTest {

    private static final Long CATEGORY_ID = 52L;

    static Stream<Arguments> matchingCategoryPaths() {
        return Stream.of(
            Arguments.of(GarmentPart.TOP, List.of("의류", "상의")),
            Arguments.of(GarmentPart.TOP, List.of("의류", "아우터")),
            Arguments.of(GarmentPart.TOP, List.of("의류", "원피스")),
            Arguments.of(GarmentPart.TOP, List.of("의류", "상의", "티셔츠")),
            Arguments.of(GarmentPart.BOTTOM, List.of("의류", "하의")),
            Arguments.of(GarmentPart.BOTTOM, List.of("의류", "치마")));
    }

    @ParameterizedTest(name = "{0} 기준 옷, 경로 {1}")
    @MethodSource("matchingCategoryPaths")
    @DisplayName("FIT-01 구분에 맞는 카테고리 경로면 기준 옷 값이 만들어진다")
    void createdWhenCategoryPathMatchesPart(GarmentPart part, List<String> categoryPathNames) {
        ReferenceGarmentSpec spec = ReferenceGarmentSpec.of(
            part, CATEGORY_ID, categoryPathNames, requiredMeasurements(part));

        assertThat(spec.part()).isEqualTo(part);
        assertThat(spec.categoryId()).isEqualTo(CATEGORY_ID);
    }

    static Stream<Arguments> mismatchingCategoryPaths() {
        return Stream.of(
            Arguments.of(GarmentPart.TOP, List.of("의류", "하의")),
            Arguments.of(GarmentPart.BOTTOM, List.of("의류", "원피스")),
            Arguments.of(GarmentPart.TOP, List.of("잡화", "모자")));
    }

    @ParameterizedTest(name = "{0} 기준 옷, 경로 {1}")
    @MethodSource("mismatchingCategoryPaths")
    @DisplayName("FIT-01 구분과 맞지 않는 카테고리 경로면 구분과 맞지 않는 세부 종류라고 거절한다")
    void rejectedWhenCategoryPathDoesNotMatchPart(GarmentPart part, List<String> categoryPathNames) {
        assertRejected(
            () -> ReferenceGarmentSpec.of(part, CATEGORY_ID, categoryPathNames, requiredMeasurements(part)),
            ReferenceGarmentErrorCode.CATEGORY_PART_MISMATCH);
    }

    private static void assertRejected(ThrowingCallable creation, ReferenceGarmentErrorCode expected) {
        assertThatThrownBy(creation)
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }

    private static Map<String, BigDecimal> requiredMeasurements(GarmentPart part) {
        if (part == GarmentPart.TOP) {
            return Map.of("TOTAL_LENGTH", cm("45.0"), "CHEST_WIDTH", cm("34.0"));
        }
        return Map.of("TOTAL_LENGTH", cm("60.0"), "WAIST_WIDTH", cm("24.0"));
    }

    private static BigDecimal cm(String value) {
        return new BigDecimal(value);
    }
}
