package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.FitProduct;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.GarmentPart;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.FitProduct.SizeMeasurements;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.ChildSizeInput;
import com.bbcc.kidly.boundedcontext.recommend.domain.wearpreview.HemPosition;
import com.bbcc.kidly.boundedcontext.recommend.domain.wearpreview.SleevePosition;
import com.bbcc.kidly.boundedcontext.recommend.domain.wearpreview.WearPreview;
import com.bbcc.kidly.boundedcontext.recommend.domain.wearpreview.WearPreviewCalculator;
import com.bbcc.kidly.boundedcontext.recommend.out.growth.GrowthTableConfig;
import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.childprofile.domain.Gender;
import com.bbcc.kidly.shared.childprofile.domain.PreferredFit;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

class WearPreviewCalculatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 12);
    // 오늘 만 6세(78개월)인 여자아이다
    private static final LocalDate BIRTH_DATE_AGE_6 = LocalDate.of(2020, 5, 1);
    private static final ChildSizeInput GIRL_120 = ChildSizeInput.of(120.0, 21.0, Gender.FEMALE, BIRTH_DATE_AGE_6,
        BodyType.NORMAL, PreferredFit.REGULAR);
    private static final List<String> TOP_PATH = List.of("의류", "상의");

    private final WearPreviewCalculator calculator =
        new WearPreviewCalculator(new GrowthTableConfig().bodyHeightRatioTable());

    @Test
    @DisplayName("PRV-01 몸 높이는 자녀 키 × 나이 · 성별 비율을 소수 첫째 자리로 반올림한 cm다")
    void bodyHeightsFromHeightAndRatio() {
        // 만 6세 여 비율: 목뒤 0.828 · 어깨 0.775 · 허리 0.605 · 엉덩이 0.481 · 샅 0.437 · 무릎 0.255 · 가쪽복사 0.043 · 팔 0.326
        WearPreview preview = calculator.preview(product(TOP_PATH, size("120", "45.0", null)), GIRL_120, TODAY)
            .orElseThrow();

        assertThat(preview.body()).isEqualTo(new WearPreview.Body(99.4, 93.0, 72.6, 57.7, 52.4, 30.6, 5.2, 39.1));
    }

    // 목뒤 99.36cm · 허리 72.6cm에서 총장을 뺀다
    @ParameterizedTest(name = "{0} 총장 {1}cm → {2}, 밑단 {3}cm")
    @CsvSource({
        "상의, 45.0, TOP, 54.4",
        "아우터, 50.0, OUTER, 49.4",
        "원피스, 60.0, DRESS, 39.4",
        "바지, 70.0, BOTTOM, 2.6",
        "치마, 30.0, SKIRT, 42.6",
    })
    @DisplayName("PRV-02 밑단 높이는 상의 · 아우터 · 원피스면 목뒤높이 − 총장, 바지 · 치마면 허리높이 − 총장이다")
    void hemHeightByGarment(String leafCategory, String totalLengthCm, GarmentPart expectedGarment,
        double expectedHemCm) {
        FitProduct product = product(List.of("의류", leafCategory), size("120", totalLengthCm, null));

        WearPreview preview = calculator.preview(product, GIRL_120, TODAY).orElseThrow();

        assertThat(preview.garment()).isEqualTo(expectedGarment);
        assertThat(preview.sizes()).extracting(WearPreview.SizeWear::size, WearPreview.SizeWear::hemCm)
            .containsExactly(tuple("120", expectedHemCm));
    }

    // 만 6세 여 120cm: 목뒤 99.36 · 허리 72.6 · 엉덩이 57.72 · 샅 52.44 · 무릎 30.6 · 가쪽복사 5.16cm.
    // 샅과 무릎의 가운데는 41.52cm, 무릎 ± 2cm는 28.6 ~ 32.6cm, 가쪽복사 ± 2cm는 3.16 ~ 7.16cm다
    @ParameterizedTest(name = "상의 총장 {0}cm(밑단 {1}cm) → {2}")
    @CsvSource({
        "25.0, 74.36, ABOVE_WAIST",
        "26.7, 72.66, ABOVE_WAIST",
        "26.8, 72.56, WAIST_HIP",
        "35.0, 64.36, WAIST_HIP",
        "45.0, 54.36, HIP",
        "50.0, 49.36, THIGH",
        "63.0, 36.36, ABOVE_KNEE",
        "66.7, 32.66, ABOVE_KNEE",
        "66.8, 32.56, KNEE",
        "69.0, 30.36, KNEE",
        "70.7, 28.66, KNEE",
        "70.8, 28.56, BELOW_KNEE",
        "80.0, 19.36, BELOW_KNEE",
        "92.1, 7.26, BELOW_KNEE",
        "92.3, 7.06, ANKLE",
        "96.1, 3.26, ANKLE",
        "96.3, 3.06, FLOOR",
    })
    @DisplayName("PRV-03 밑단 위치는 허리 · 엉덩이 · 샅 · 샅과 무릎의 가운데를 넘을 때마다 바뀌고, 무릎 · 가쪽복사 ± 2cm는 KNEE · ANKLE이다")
    void hemPositionByBodyHeights(String totalLengthCm, double hemCm, HemPosition expected) {
        FitProduct product = product(TOP_PATH, size("120", totalLengthCm, null));

        WearPreview preview = calculator.preview(product, GIRL_120, TODAY).orElseThrow();

        assertThat(preview.sizes()).extracting(WearPreview.SizeWear::hemAt).containsExactly(expected);
    }

    // 만 6세 여 120cm의 팔길이는 39.12cm라, 절반은 19.56cm, 팔길이 ± 2cm는 37.12 ~ 41.12cm다
    @ParameterizedTest(name = "소매길이 {0}cm → {1}")
    @CsvSource({
        "15.0, ELBOW",
        "19.5, ELBOW",
        "19.6, ABOVE_WRIST",
        "30.0, ABOVE_WRIST",
        "37.1, ABOVE_WRIST",
        "37.2, WRIST",
        "39.0, WRIST",
        "41.1, WRIST",
        "41.2, HAND",
        "43.0, HAND",
    })
    @DisplayName("PRV-04 소매 위치는 팔길이의 절반 이하면 ELBOW, 팔길이 ± 2cm면 WRIST, 그 사이는 ABOVE_WRIST, 더 길면 HAND다")
    void sleevePositionByArmLength(String sleeveLengthCm, SleevePosition expected) {
        FitProduct product = product(TOP_PATH, size("120", "45.0", sleeveLengthCm));

        WearPreview preview = calculator.preview(product, GIRL_120, TODAY).orElseThrow();

        assertThat(preview.sizes()).extracting(WearPreview.SizeWear::sleeveCm, WearPreview.SizeWear::sleeveAt)
            .containsExactly(tuple(Double.valueOf(sleeveLengthCm), expected));
    }

    static Stream<Arguments> notPreviewedProducts() {
        return Stream.of(
            Arguments.of(Named.of("사이즈 무관 상의",
                new FitProduct(1L, TOP_PATH, true, List.of(size("FREE", "45.0", "30.0"))))),
            Arguments.of(Named.of("모자", product(List.of("잡화", "모자"), size("52", "20.0", null)))),
            Arguments.of(Named.of("양말", product(List.of("잡화", "양말"), size("150", "15.0", null))))
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("notPreviewedProducts")
    @DisplayName("PRV-05 사이즈 무관 상품 · 모자 · 양말은 착용 미리보기를 만들지 않는다")
    void noPreviewForSizeFreeHatAndSocks(FitProduct product) {
        assertThat(calculator.preview(product, GIRL_120, TODAY)).isEmpty();
    }

    @Test
    @DisplayName("PRV-05 총장이 없는 사이즈는 밑단 칸이, 소매길이가 없는 사이즈와 바지 · 치마는 소매 칸이 null이다")
    void emptyFieldsWithoutMeasurement() {
        WearPreview top = calculator.preview(product(TOP_PATH,
            size("110", null, "30.0"), size("120", "45.0", null)), GIRL_120, TODAY).orElseThrow();
        WearPreview pants = calculator.preview(product(List.of("의류", "바지"),
            size("120", "70.0", "30.0")), GIRL_120, TODAY).orElseThrow();

        assertThat(top.sizes()).containsExactly(
            new WearPreview.SizeWear("110", null, null, 30.0, SleevePosition.ABOVE_WRIST),
            new WearPreview.SizeWear("120", 54.4, HemPosition.HIP, null, null));
        assertThat(pants.sizes()).containsExactly(
            new WearPreview.SizeWear("120", 2.6, HemPosition.FLOOR, null, null));
    }

    static Stream<Arguments> nullInputs() {
        FitProduct product = product(TOP_PATH, size("120", "45.0", null));
        return Stream.of(
            Arguments.of(Named.of("상품", (FitProduct) null), GIRL_120, TODAY, "product는 필수입니다"),
            Arguments.of(Named.of("자녀", product), null, TODAY, "child는 필수입니다"),
            Arguments.of(Named.of("오늘 날짜", product), GIRL_120, null, "today는 필수입니다"),
            Arguments.of(Named.of("성별", product), ChildSizeInput.of(120.0, 21.0, null, BIRTH_DATE_AGE_6,
                BodyType.NORMAL, PreferredFit.REGULAR), TODAY, "gender는 필수입니다"),
            Arguments.of(Named.of("생년월일", product), ChildSizeInput.of(120.0, 21.0, Gender.FEMALE, null,
                BodyType.NORMAL, PreferredFit.REGULAR), TODAY, "birthDate는 필수입니다")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("nullInputs")
    @DisplayName("PRV-01 착용 미리보기 계산에 필요한 값이 null이면 계산하지 않는다")
    void rejectNullInputs(FitProduct product, ChildSizeInput child, LocalDate today, String expectedMessage) {
        assertThatThrownBy(() -> calculator.preview(product, child, today))
            .isInstanceOf(NullPointerException.class)
            .hasMessage(expectedMessage);
    }

    private static FitProduct product(List<String> categoryPathNames, SizeMeasurements... sizes) {
        return new FitProduct(1L, categoryPathNames, false, List.of(sizes));
    }

    // 총장 · 소매길이가 null이면 그 실측이 없는 사이즈다
    private static SizeMeasurements size(String size, String totalLengthCm, String sleeveLengthCm) {
        Map<String, BigDecimal> measurements = new HashMap<>();
        if (totalLengthCm != null) {
            measurements.put("TOTAL_LENGTH", new BigDecimal(totalLengthCm));
        }
        if (sleeveLengthCm != null) {
            measurements.put("SLEEVE_LENGTH", new BigDecimal(sleeveLengthCm));
        }
        return new SizeMeasurements(size, measurements);
    }
}
