package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.GarmentCategoryPathEntry;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.GarmentPart;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.ReferenceGarmentErrorCode;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.ReferenceGarmentSpec;
import com.bbcc.kidly.global.error.BusinessException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
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
            part, CATEGORY_ID, path(categoryPathNames), requiredMeasurements(part));

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
            () -> ReferenceGarmentSpec.of(part, CATEGORY_ID, path(categoryPathNames), requiredMeasurements(part)),
            ReferenceGarmentErrorCode.CATEGORY_PART_MISMATCH);
    }

    static Stream<Arguments> unresolvedCategoryPaths() {
        return Stream.of(
            Arguments.of(GarmentPart.TOP, null),
            Arguments.of(GarmentPart.TOP, List.of()),
            Arguments.of(GarmentPart.BOTTOM, null),
            Arguments.of(GarmentPart.BOTTOM, List.of()));
    }

    @ParameterizedTest(name = "{0} 기준 옷, 경로 {1}")
    @MethodSource("unresolvedCategoryPaths")
    @DisplayName("카테고리 경로를 찾지 못했으면 확인할 수 없는 세부 종류라고 거절한다")
    void rejectedWhenCategoryPathIsNotFound(GarmentPart part, List<String> categoryPathNames) {
        assertRejected(
            () -> ReferenceGarmentSpec.of(part, CATEGORY_ID, path(categoryPathNames), requiredMeasurements(part)),
            ReferenceGarmentErrorCode.CATEGORY_NOT_FOUND);
    }

    static Stream<Arguments> categoryIdsOutsideLeafOfPath() {
        List<GarmentCategoryPathEntry> topPath = List.of(
            new GarmentCategoryPathEntry(1L, "의류"),
            new GarmentCategoryPathEntry(5L, "상의"),
            new GarmentCategoryPathEntry(52L, "티셔츠"));
        return Stream.of(
            Arguments.of(Named.of("대분류 ID", 1L), topPath),
            Arguments.of(Named.of("중간 카테고리 ID", 5L), topPath),
            Arguments.of(Named.of("경로에 없는 ID", 99L), topPath));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("categoryIdsOutsideLeafOfPath")
    @DisplayName("경로의 마지막 칸이 세부 종류 ID가 아니면 확인할 수 없는 세부 종류라고 거절한다")
    void rejectedWhenLastEntryOfPathIsNotTheCategoryId(Long categoryId, List<GarmentCategoryPathEntry> categoryPath) {
        assertRejected(
            () -> ReferenceGarmentSpec.of(GarmentPart.TOP, categoryId, categoryPath,
                requiredMeasurements(GarmentPart.TOP)),
            ReferenceGarmentErrorCode.CATEGORY_NOT_FOUND);
    }

    static Stream<Arguments> acceptedMeasurements() {
        return Stream.of(
            Arguments.of(GarmentPart.TOP, List.of("의류", "상의"),
                Map.of("TOTAL_LENGTH", cm("45.0"), "CHEST_WIDTH", cm("34.0"))),
            Arguments.of(GarmentPart.TOP, List.of("의류", "상의"),
                Map.of("TOTAL_LENGTH", cm("45.0"), "CHEST_WIDTH", cm("34.0"), "SHOULDER_WIDTH", cm("30.0"))),
            Arguments.of(GarmentPart.TOP, List.of("의류", "상의"),
                Map.of("TOTAL_LENGTH", cm("45.0"), "CHEST_WIDTH", cm("34.0"),
                    "SHOULDER_WIDTH", cm("30.0"), "SLEEVE_LENGTH", cm("38.5"))),
            Arguments.of(GarmentPart.BOTTOM, List.of("의류", "하의"),
                Map.of("TOTAL_LENGTH", cm("60.0"), "WAIST_WIDTH", cm("24.0"))),
            Arguments.of(GarmentPart.BOTTOM, List.of("의류", "하의"),
                Map.of("TOTAL_LENGTH", cm("60.0"), "WAIST_WIDTH", cm("24.0"), "RISE", cm("20.0"))));
    }

    @ParameterizedTest(name = "{0} 기준 옷, 실측 {2}")
    @MethodSource("acceptedMeasurements")
    @DisplayName("필수 실측만 넣어도, 선택 실측까지 넣어도 기준 옷 값이 만들어지고 실측은 넣은 그대로다")
    void createdWithRequiredOnlyOrWithOptionalMeasurements(
        GarmentPart part, List<String> categoryPathNames, Map<String, BigDecimal> measurements) {
        ReferenceGarmentSpec spec = ReferenceGarmentSpec.of(part, CATEGORY_ID, path(categoryPathNames), measurements);

        assertThat(spec.measurements()).isEqualTo(measurements);
    }

    @Test
    @DisplayName("만든 뒤에 넘겨 준 실측 Map을 바꿔도 기준 옷 값의 실측은 그대로다")
    void measurementsAreNotAffectedByLaterChangeOfGivenMap() {
        Map<String, BigDecimal> given = new HashMap<>(requiredMeasurements(GarmentPart.TOP));
        ReferenceGarmentSpec spec = ReferenceGarmentSpec.of(
            GarmentPart.TOP, CATEGORY_ID, path(List.of("의류", "상의")), given);

        given.put("SLEEVE_LENGTH", cm("38.5"));

        assertThat(spec.measurements()).containsOnlyKeys("TOTAL_LENGTH", "CHEST_WIDTH");
    }

    @Test
    @DisplayName("기준 옷 값의 실측은 밖에서 바꿀 수 없다")
    void measurementsCannotBeChangedFromOutside() {
        ReferenceGarmentSpec spec = ReferenceGarmentSpec.of(GarmentPart.TOP, CATEGORY_ID,
            path(List.of("의류", "상의")), new HashMap<>(requiredMeasurements(GarmentPart.TOP)));

        assertThatThrownBy(() -> spec.measurements().put("SLEEVE_LENGTH", cm("38.5")))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    static Stream<Arguments> measurementsMissingRequired() {
        return Stream.of(
            Arguments.of(GarmentPart.TOP, null),
            Arguments.of(GarmentPart.TOP, Map.of()),
            Arguments.of(GarmentPart.TOP, Map.of("TOTAL_LENGTH", cm("45.0"))),
            Arguments.of(GarmentPart.TOP, Map.of("CHEST_WIDTH", cm("34.0"))),
            Arguments.of(GarmentPart.TOP, Map.of("SHOULDER_WIDTH", cm("30.0"), "SLEEVE_LENGTH", cm("38.5"))),
            Arguments.of(GarmentPart.BOTTOM, null),
            Arguments.of(GarmentPart.BOTTOM, Map.of()),
            Arguments.of(GarmentPart.BOTTOM, Map.of("TOTAL_LENGTH", cm("60.0"))),
            Arguments.of(GarmentPart.BOTTOM, Map.of("WAIST_WIDTH", cm("24.0"))),
            Arguments.of(GarmentPart.BOTTOM, Map.of("RISE", cm("20.0"))));
    }

    @ParameterizedTest(name = "{0} 기준 옷, 실측 {1}")
    @MethodSource("measurementsMissingRequired")
    @DisplayName("필수 실측이 하나라도 없으면 필수 실측이 없다고 거절한다")
    void rejectedWhenRequiredMeasurementIsMissing(GarmentPart part, Map<String, BigDecimal> measurements) {
        assertRejected(
            () -> ReferenceGarmentSpec.of(part, CATEGORY_ID, matchingPath(part), measurements),
            ReferenceGarmentErrorCode.REQUIRED_MEASUREMENT_MISSING);
    }

    @ParameterizedTest(name = "{0} 기준 옷에 {1}")
    @CsvSource({
        "TOP, WAIST_WIDTH",
        "TOP, RISE",
        "TOP, FOO",
        "BOTTOM, CHEST_WIDTH",
        "BOTTOM, SHOULDER_WIDTH",
        "BOTTOM, SLEEVE_LENGTH",
        "BOTTOM, FOO",
    })
    @DisplayName("그 구분에 없는 실측 항목 코드가 있으면 이 구분에 없는 실측 항목이라고 거절한다")
    void rejectedWhenMeasurementCodeIsNotForThePart(GarmentPart part, String unsupportedCode) {
        Map<String, BigDecimal> measurements = new HashMap<>(requiredMeasurements(part));
        measurements.put(unsupportedCode, cm("20.0"));

        assertRejected(
            () -> ReferenceGarmentSpec.of(part, CATEGORY_ID, matchingPath(part), measurements),
            ReferenceGarmentErrorCode.UNSUPPORTED_MEASUREMENT);
    }

    static Stream<Arguments> invalidMeasurementValues() {
        return Stream.of(
            Arguments.of(GarmentPart.TOP, "TOTAL_LENGTH", cm("0")),
            Arguments.of(GarmentPart.TOP, "TOTAL_LENGTH", cm("0.0")),
            Arguments.of(GarmentPart.TOP, "TOTAL_LENGTH", cm("-1")),
            Arguments.of(GarmentPart.TOP, "CHEST_WIDTH", cm("200.1")),
            Arguments.of(GarmentPart.TOP, "CHEST_WIDTH", cm("420")),
            Arguments.of(GarmentPart.TOP, "SLEEVE_LENGTH", cm("40.25")),
            Arguments.of(GarmentPart.TOP, "SHOULDER_WIDTH", null),
            Arguments.of(GarmentPart.BOTTOM, "WAIST_WIDTH", cm("0.05")),
            Arguments.of(GarmentPart.BOTTOM, "WAIST_WIDTH", null),
            Arguments.of(GarmentPart.BOTTOM, "RISE", cm("-0.1")));
    }

    @ParameterizedTest(name = "{0} 기준 옷의 {1} = {2}")
    @MethodSource("invalidMeasurementValues")
    @DisplayName("실측 값이 0 이하이거나 200을 넘거나 소수 둘째 자리가 있거나 비어 있으면 잘못된 실측 값이라고 거절한다")
    void rejectedWhenMeasurementValueIsInvalid(GarmentPart part, String code, BigDecimal invalidValue) {
        Map<String, BigDecimal> measurements = new HashMap<>(requiredMeasurements(part));
        measurements.put(code, invalidValue);

        assertRejected(
            () -> ReferenceGarmentSpec.of(part, CATEGORY_ID, matchingPath(part), measurements),
            ReferenceGarmentErrorCode.INVALID_MEASUREMENT_VALUE);
    }

    @ParameterizedTest(name = "{0}cm는 {1}cm로 보관된다")
    @CsvSource({
        "0.1, 0.1",
        "1, 1.0",
        "40, 40.0",
        "40.0, 40.0",
        "40.50, 40.5",
        "200, 200.0",
        "200.0, 200.0",
    })
    @DisplayName("실측 값이 0보다 크고 200 이하이며 소수 첫째 자리까지면 받아, 소수 첫째 자리 하나로 보관한다")
    void createdWhenMeasurementValueIsOnTheBoundary(String value, String expectedStored) {
        Map<String, BigDecimal> measurements = Map.of("TOTAL_LENGTH", cm(value), "CHEST_WIDTH", cm("34.0"));

        ReferenceGarmentSpec spec = ReferenceGarmentSpec.of(
            GarmentPart.TOP, CATEGORY_ID, path(List.of("의류", "상의")), measurements);

        assertThat(spec.measurements().get("TOTAL_LENGTH")).isEqualTo(cm(expectedStored));
    }

    static Stream<Arguments> inputsWithSeveralProblems() {
        List<String> topPath = List.of("의류", "상의");
        List<String> bottomPath = List.of("의류", "하의");
        Map<String, BigDecimal> onlyTotal = Map.of("TOTAL_LENGTH", cm("45.0"));
        Map<String, BigDecimal> waistInsteadOfChest = Map.of("TOTAL_LENGTH", cm("45.0"), "WAIST_WIDTH", cm("24.0"));
        Map<String, BigDecimal> unknownCodeAndTooLong = Map.of("TOTAL_LENGTH", cm("45.0"), "CHEST_WIDTH", cm("34.0"),
            "FOO", cm("420"));
        Map<String, BigDecimal> onlyTooLongTotal = Map.of("TOTAL_LENGTH", cm("420"));
        return Stream.of(
            Arguments.of("맞지 않는 세부 종류 + 필수 누락", bottomPath, onlyTotal,
                ReferenceGarmentErrorCode.CATEGORY_PART_MISMATCH),
            Arguments.of("맞지 않는 세부 종류 + 잘못된 값", bottomPath, onlyTooLongTotal,
                ReferenceGarmentErrorCode.CATEGORY_PART_MISMATCH),
            Arguments.of("경로 없음 + 이 구분에 없는 항목", null, waistInsteadOfChest,
                ReferenceGarmentErrorCode.CATEGORY_NOT_FOUND),
            Arguments.of("이 구분에 없는 항목 + 필수 누락", topPath, waistInsteadOfChest,
                ReferenceGarmentErrorCode.UNSUPPORTED_MEASUREMENT),
            Arguments.of("이 구분에 없는 항목 + 잘못된 값", topPath, unknownCodeAndTooLong,
                ReferenceGarmentErrorCode.UNSUPPORTED_MEASUREMENT),
            Arguments.of("필수 누락 + 잘못된 값", topPath, onlyTooLongTotal,
                ReferenceGarmentErrorCode.REQUIRED_MEASUREMENT_MISSING));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("inputsWithSeveralProblems")
    @DisplayName("문제가 여러 개면 세부 종류, 이 구분에 없는 항목, 필수 누락, 값 순서로 먼저 걸린 하나만 거절한다")
    void rejectedByTheFirstFailedCheck(String problems, List<String> categoryPathNames,
        Map<String, BigDecimal> measurements, ReferenceGarmentErrorCode expected) {
        assertRejected(
            () -> ReferenceGarmentSpec.of(GarmentPart.TOP, CATEGORY_ID, path(categoryPathNames), measurements),
            expected);
    }

    static Stream<Arguments> creationsWithoutPartOrCategoryId() {
        List<GarmentCategoryPathEntry> topPath = matchingPath(GarmentPart.TOP);
        Map<String, BigDecimal> measurements = requiredMeasurements(GarmentPart.TOP);
        return Stream.of(
            Arguments.of(Named.of("구분 없음", (ThrowingCallable) () ->
                ReferenceGarmentSpec.of(null, CATEGORY_ID, topPath, measurements))),
            Arguments.of(Named.of("세부 종류 ID 없음", (ThrowingCallable) () ->
                ReferenceGarmentSpec.of(GarmentPart.TOP, null, topPath, measurements))));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("creationsWithoutPartOrCategoryId")
    @DisplayName("구분이나 세부 종류 ID가 없으면 기준 옷 값을 만들지 않는다")
    void rejectedWithoutPartOrCategoryId(ThrowingCallable creation) {
        assertThatThrownBy(creation)
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("필수입니다");
    }

    private static List<GarmentCategoryPathEntry> matchingPath(GarmentPart part) {
        return path(part == GarmentPart.TOP ? List.of("의류", "상의") : List.of("의류", "하의"));
    }

    private static List<GarmentCategoryPathEntry> path(List<String> names) {
        if (names == null) {
            return null;
        }
        List<GarmentCategoryPathEntry> entries = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            boolean last = i == names.size() - 1;
            entries.add(new GarmentCategoryPathEntry(last ? CATEGORY_ID : (long) i + 1, names.get(i)));
        }
        return entries;
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
