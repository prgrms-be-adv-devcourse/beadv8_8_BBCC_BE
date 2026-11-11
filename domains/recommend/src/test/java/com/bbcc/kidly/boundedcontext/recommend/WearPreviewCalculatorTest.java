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

    // 10세 남아(126개월)다
    private static final LocalDate BIRTH_DATE_AGE_10 = LocalDate.of(2016, 5, 1);
    // 표 CSV는 클래스에서 한 번만 읽는다
    private static final WearPreviewCalculator CALCULATOR =
        new WearPreviewCalculator(new GrowthTableConfig().bodyHeightRatioTable());

    private final WearPreviewCalculator calculator = CALCULATOR;

    @Test
    @DisplayName("PRV-01 몸 높이는 자녀 키 × 나이 · 성별 비율을 소수 첫째 자리로 반올림한 cm다")
    void bodyHeightsFromHeightAndRatio() {
        // 만 6세 여 비율: 목뒤 0.822 · 어깨 0.773 · 허리 0.599 · 엉덩이 0.490 · 샅 0.446 · 무릎 0.254 · 가쪽복사 0.044 · 팔 0.328
        WearPreview preview = calculator.preview(product(TOP_PATH, size("120", "45.0", null)), GIRL_120, TODAY)
            .orElseThrow();

        assertThat(preview.body()).isEqualTo(new WearPreview.Body(98.6, 92.8, 71.9, 58.8, 53.5, 30.5, 5.3, 39.4));
    }

    // 목뒤 98.64cm · 허리 71.88cm에서 총장을 뺀다
    @ParameterizedTest(name = "{0} 총장 {1}cm → {2}, 밑단 {3}cm")
    @CsvSource({
        "상의, 45.0, TOP, 53.6",
        "아우터, 50.0, OUTER, 48.6",
        "원피스, 60.0, DRESS, 38.6",
        "바지, 70.0, BOTTOM, 1.9",
        "치마, 30.0, SKIRT, 41.9",
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

    // 만 6세 여 120cm: 목뒤 98.64 · 허리 71.88 · 엉덩이 58.8 · 샅 53.52 · 무릎 30.48 · 가쪽복사 5.28cm.
    // 샅과 무릎의 가운데는 42.0cm, 무릎 ± 2cm는 28.48 ~ 32.48cm, 가쪽복사 ± 2cm는 3.28 ~ 7.28cm다.
    // 경계마다 바로 위 · 아래 값을 넣는다
    @ParameterizedTest(name = "상의 총장 {0}cm(밑단 {1}cm) → {2}")
    @CsvSource({
        "25.0, 73.64, ABOVE_WAIST",
        "26.7, 71.94, ABOVE_WAIST",
        "26.8, 71.84, WAIST_HIP",
        "35.0, 63.64, WAIST_HIP",
        "39.8, 58.84, WAIST_HIP",
        "39.9, 58.74, HIP",
        "42.0, 56.64, HIP",
        "45.1, 53.54, HIP",
        "45.2, 53.44, THIGH",
        "50.0, 48.64, THIGH",
        "56.6, 42.04, THIGH",
        "56.7, 41.94, ABOVE_KNEE",
        "63.0, 35.64, ABOVE_KNEE",
        "66.1, 32.54, ABOVE_KNEE",
        "66.2, 32.44, KNEE",
        "68.0, 30.64, KNEE",
        "70.1, 28.54, KNEE",
        "70.2, 28.44, BELOW_KNEE",
        "80.0, 18.64, BELOW_KNEE",
        "91.3, 7.34, BELOW_KNEE",
        "91.4, 7.24, ANKLE",
        "94.0, 4.64, ANKLE",
        "95.3, 3.34, ANKLE",
        "95.4, 3.24, FLOOR",
        "97.0, 1.64, FLOOR",
    })
    @DisplayName("PRV-03 밑단 위치는 허리 · 엉덩이 · 샅 · 샅과 무릎의 가운데를 넘을 때마다 바뀌고, 무릎 · 가쪽복사 ± 2cm는 KNEE · ANKLE이다")
    void hemPositionByBodyHeights(String totalLengthCm, double hemCm, HemPosition expected) {
        FitProduct product = product(TOP_PATH, size("120", totalLengthCm, null));

        WearPreview preview = calculator.preview(product, GIRL_120, TODAY).orElseThrow();

        assertThat(preview.sizes()).extracting(WearPreview.SizeWear::hemAt).containsExactly(expected);
    }

    // 만 6세 여 120cm의 위팔길이(어깨에서 팔꿈치)는 22.92cm, 팔길이는 39.36cm라 팔길이 ± 2cm는 37.36 ~ 41.36cm다
    @ParameterizedTest(name = "소매길이 {0}cm → {1}")
    @CsvSource({
        "15.0, ELBOW",
        "22.9, ELBOW",
        "23.0, ABOVE_WRIST",
        "30.0, ABOVE_WRIST",
        "37.3, ABOVE_WRIST",
        "37.4, WRIST",
        "39.0, WRIST",
        "41.3, WRIST",
        "41.4, HAND",
        "43.0, HAND",
    })
    @DisplayName("PRV-04 소매 위치는 위팔길이(팔꿈치) 이하면 ELBOW, 팔길이 ± 2cm면 WRIST, 그 사이는 ABOVE_WRIST, 더 길면 HAND다")
    void sleevePositionByArmLength(String sleeveLengthCm, SleevePosition expected) {
        FitProduct product = product(TOP_PATH, size("120", "45.0", sleeveLengthCm));

        WearPreview preview = calculator.preview(product, GIRL_120, TODAY).orElseThrow();

        assertThat(preview.sizes()).extracting(WearPreview.SizeWear::sleeveCm, WearPreview.SizeWear::sleeveAt)
            .containsExactly(tuple(Double.valueOf(sleeveLengthCm), expected));
    }

    @Test
    @DisplayName("PRV-01 남아 · 다른 나이는 그 나이 · 성별 비율로 몸 높이와 밑단 위치를 구한다")
    void boyUsesOwnAgeAndGenderRatio() {
        // 만 10세 남 140cm: 목뒤 0.841 → 117.74, 허리 0.606 → 84.84, 가쪽복사 0.045 → 6.3cm.
        // 바지 총장 80cm면 밑단 4.84cm라 가쪽복사 ± 2cm(4.3 ~ 8.3cm) 안이다. 여아 비율이면 목뒤 117.5cm다
        ChildSizeInput boy = ChildSizeInput.of(140.0, 33.0, Gender.MALE, BIRTH_DATE_AGE_10, BodyType.NORMAL,
            PreferredFit.REGULAR);

        WearPreview preview = calculator.preview(product(List.of("의류", "바지"), size("140", "80.0", null)), boy,
            TODAY).orElseThrow();

        assertThat(preview.body().neckCm()).isEqualTo(117.7);
        assertThat(preview.body().waistCm()).isEqualTo(84.8);
        assertThat(preview.sizes()).extracting(WearPreview.SizeWear::hemAt).containsExactly(HemPosition.ANKLE);
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
            new WearPreview.SizeWear("120", 53.6, HemPosition.HIP, null, null));
        assertThat(pants.sizes()).containsExactly(
            new WearPreview.SizeWear("120", 1.9, HemPosition.FLOOR, null, null));
    }

    static Stream<Arguments> nullPreviewParts() {
        WearPreview.Body body = new WearPreview.Body(98.6, 92.8, 71.9, 58.8, 53.5, 30.5, 5.3, 39.4);
        return Stream.of(
            Arguments.of(Named.of("옷 종류", (Runnable) () -> new WearPreview(null, body, List.of())),
                "garment는 필수입니다"),
            Arguments.of(Named.of("몸 높이", (Runnable) () -> new WearPreview(GarmentPart.TOP, null, List.of())),
                "body는 필수입니다"),
            Arguments.of(Named.of("사이즈 목록", (Runnable) () -> new WearPreview(GarmentPart.TOP, body, null)),
                "sizes는 필수입니다")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("nullPreviewParts")
    @DisplayName("PRV-05 착용 미리보기는 옷 종류 · 몸 높이 · 사이즈 목록이 모두 있어야 만든다")
    void rejectNullPreviewParts(Runnable creation, String expectedMessage) {
        assertThatThrownBy(creation::run)
            .isInstanceOf(NullPointerException.class)
            .hasMessage(expectedMessage);
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
