package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.bbcc.kidly.boundedcontext.recommend.domain.growth.WearablePeriod;
import com.bbcc.kidly.boundedcontext.recommend.domain.growth.WearablePeriodCalculator;
import com.bbcc.kidly.boundedcontext.recommend.domain.growth.WearablePeriodCalculator.WearInfo;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.GarmentPart;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.ReferenceFit;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.ReferenceFit.Item;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.ReferenceFit.SizeDiff;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.ProductSizes;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.SizeRange;
import com.bbcc.kidly.boundedcontext.recommend.out.growth.GrowthTableConfig;
import com.bbcc.kidly.shared.childprofile.domain.Gender;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class WearablePeriodCalculatorTest {

    // 오늘 만 6세(78개월)인 여자아이, 키 120cm, 한 달 0.5cm씩 큰다
    private static final long AGE_6_MONTHS = 78;
    private static final double HEIGHT_CM = 120.0;
    private static final double CM_PER_MONTH = 0.5;
    private static final List<String> TOP_PATH = List.of("의류", "상의");
    private static final List<String> SKIRT_PATH = List.of("의류", "치마");
    // 권장 키 상한이 넉넉해 길이 기준은 40개월이다
    private static final ProductSizes LONG_SIZES = ProductSizes.of(false, List.of(
        SizeRange.of("120", 115, 140),
        SizeRange.of("130", 125, 140)));

    private final WearablePeriodCalculator calculator =
        new WearablePeriodCalculator(new GrowthTableConfig().girthRatioTable());

    @Test
    @DisplayName("WEAR-01 길이 기준은 (권장 키 상한 − 지금 키) ÷ 한 달 성장량을 내린 개월 수다")
    void lengthMonthsFromMaxHeight() {
        ProductSizes sizes = ProductSizes.of(false, List.of(
            SizeRange.of("120", 115, 130),
            SizeRange.of("125", 120, 125)));

        List<WearablePeriod> periods = calculator.calculate(
            new WearInfo(sizes, TOP_PATH, Gender.FEMALE, AGE_6_MONTHS, HEIGHT_CM + 0.4, CM_PER_MONTH, null));

        // (130 − 120.4) ÷ 0.5 = 19.2 → 19, (125 − 120.4) ÷ 0.5 = 9.2 → 9
        assertThat(periods).containsExactly(
            new WearablePeriod("120", 19, null, 19),
            new WearablePeriod("125", 9, null, 9));
    }

    @Test
    @DisplayName("WEAR-02 상의 너비 기준은 가슴단면 차이 ÷ (한 달 성장량 × 가슴 비율 ÷ 2)를 내린 개월 수이고 어깨너비는 쓰지 않는다")
    void widthMonthsFromChest() {
        // 만 6세 여 가슴 비율 0.30: 1 ÷ (0.5 × 0.30 ÷ 2) = 13.3 → 13. 130 사이즈는 비교한 차이가 없다
        ReferenceFit fit = new ReferenceFit(GarmentPart.TOP, GarmentPart.TOP, List.of(
            item("CHEST_WIDTH", "30.0", "31.0"),
            item("SHOULDER_WIDTH", "28.0", "28.2")));

        List<WearablePeriod> periods = calculator.calculate(
            new WearInfo(LONG_SIZES, TOP_PATH, Gender.FEMALE, AGE_6_MONTHS, HEIGHT_CM, CM_PER_MONTH, fit));

        assertThat(periods).extracting(WearablePeriod::size, WearablePeriod::widthMonths)
            .containsExactly(tuple("120", 13), tuple("130", null));
    }

    @Test
    @DisplayName("WEAR-02 너비 항목이 여럿이면 부위마다 계산해 가장 짧은 개월 수를 쓴다")
    void widthMonthsIsShortestPart() {
        // 허리 비율 0.25: 1 ÷ 0.0625 = 16, 엉덩이 비율 0.38: 1 ÷ 0.095 = 10.5 → 10
        ReferenceFit fit = new ReferenceFit(GarmentPart.SKIRT, GarmentPart.SKIRT, List.of(
            item("WAIST_WIDTH", "25.0", "26.0"),
            item("HIP_WIDTH", "33.0", "34.0")));

        List<WearablePeriod> periods = calculator.calculate(
            new WearInfo(LONG_SIZES, SKIRT_PATH, Gender.FEMALE, AGE_6_MONTHS, HEIGHT_CM, CM_PER_MONTH, fit));

        assertThat(periods).extracting(WearablePeriod::size, WearablePeriod::widthMonths)
            .containsExactly(tuple("120", 10), tuple("130", null));
    }

    @Test
    @DisplayName("WEAR-03 길이 기준과 너비 기준이 둘 다 있으면 짧은 쪽을 쓴다")
    void monthsIsShorterOfLengthAndWidth() {
        // 120: 길이 (125 − 120) ÷ 0.5 = 10, 가슴 +3cm → 40 → 24 / 130: 길이 40 → 24, 가슴 +1cm → 13
        ProductSizes sizes = ProductSizes.of(false, List.of(
            SizeRange.of("120", 115, 125),
            SizeRange.of("130", 125, 140)));
        ReferenceFit fit = new ReferenceFit(GarmentPart.TOP, GarmentPart.TOP, List.of(
            new Item("CHEST_WIDTH", new BigDecimal("30.0"), List.of(
                new SizeDiff("120", new BigDecimal("33.0"), new BigDecimal("3.0")),
                new SizeDiff("130", new BigDecimal("31.0"), new BigDecimal("1.0"))))));

        List<WearablePeriod> periods = calculator.calculate(
            new WearInfo(sizes, TOP_PATH, Gender.FEMALE, AGE_6_MONTHS, HEIGHT_CM, CM_PER_MONTH, fit));

        assertThat(periods).containsExactly(
            new WearablePeriod("120", 10, 24, 10),
            new WearablePeriod("130", 24, 13, 13));
    }

    @Test
    @DisplayName("WEAR-03 개월 수가 0 이하면 0, 24개월을 넘으면 24개월로 맞춘다")
    void clampMonthsBetweenZeroAndTwentyFour() {
        // 110: 길이 (118 − 120) ÷ 0.5 = −4 → 0, 가슴 −1cm → −13.3 → 0 / 140: 길이 40 → 24, 너비 없음
        ProductSizes sizes = ProductSizes.of(false, List.of(
            SizeRange.of("110", 105, 118),
            SizeRange.of("140", 125, 140)));
        ReferenceFit fit = new ReferenceFit(GarmentPart.TOP, GarmentPart.TOP, List.of(
            new Item("CHEST_WIDTH", new BigDecimal("30.0"), List.of(
                new SizeDiff("110", new BigDecimal("29.0"), new BigDecimal("-1.0"))))));

        List<WearablePeriod> periods = calculator.calculate(
            new WearInfo(sizes, TOP_PATH, Gender.FEMALE, AGE_6_MONTHS, HEIGHT_CM, CM_PER_MONTH, fit));

        assertThat(periods).containsExactly(
            new WearablePeriod("110", 0, 0, 0),
            new WearablePeriod("140", 24, null, 24));
    }

    @Test
    @DisplayName("WEAR-03 한 달 성장량이 0이면 길이 · 너비 기준을 계산하지 않는다")
    void noMonthsWhenNotGrowing() {
        ReferenceFit fit = new ReferenceFit(GarmentPart.TOP, GarmentPart.TOP, List.of(
            item("CHEST_WIDTH", "30.0", "31.0")));

        List<WearablePeriod> periods = calculator.calculate(
            new WearInfo(LONG_SIZES, TOP_PATH, Gender.FEMALE, AGE_6_MONTHS, HEIGHT_CM, 0.0, fit));

        assertThat(periods).containsExactly(
            new WearablePeriod("120", null, null, null),
            new WearablePeriod("130", null, null, null));
    }

    static Stream<Arguments> notCalculatedProducts() {
        return Stream.of(
            Arguments.of(Named.of("사이즈 무관 상의", ProductSizes.of(true, List.of())), List.of("의류", "상의")),
            Arguments.of(Named.of("모자", LONG_SIZES), List.of("잡화", "모자")),
            Arguments.of(Named.of("양말", LONG_SIZES), List.of("잡화", "양말"))
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("notCalculatedProducts")
    @DisplayName("WEAR-04 사이즈 무관 상품 · 모자 · 양말은 입을 수 있는 기간을 계산하지 않는다")
    void noPeriodsForSizeFreeHatAndSocks(ProductSizes sizes, List<String> categoryPathNames) {
        List<WearablePeriod> periods = calculator.calculate(
            new WearInfo(sizes, categoryPathNames, Gender.FEMALE, AGE_6_MONTHS, HEIGHT_CM, CM_PER_MONTH, null));

        assertThat(periods).isEmpty();
    }

    // 120 사이즈 하나의 차이만 담은 항목이다
    private static Item item(String code, String garmentCm, String productCm) {
        BigDecimal garment = new BigDecimal(garmentCm);
        BigDecimal product = new BigDecimal(productCm);
        return new Item(code, garment, List.of(new SizeDiff("120", product, product.subtract(garment))));
    }
}
