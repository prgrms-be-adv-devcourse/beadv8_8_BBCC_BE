package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.FitProduct;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.GarmentCategoryPathEntry;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.GarmentPart;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.ReferenceFit;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.ReferenceGarment;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.ReferenceGarmentSpec;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

class ReferenceFitTest {

    private static final Long CHILD_ID = 11L;

    private static final Long CLOTHING_ID = 1L;

    private static final Long TOP_ID = 52L;

    private static final Long PANTS_ID = 60L;

    private static final Long OUTER_ID = 54L;

    private static final Long OLD_BOTTOM_ID = 59L;

    @Test
    @DisplayName("FIT-06 상의 상품과 상의 칸을 비교하면 사이즈마다 상품 실측에서 기준 옷 실측을 뺀 cm를 항목 순서대로 구한다")
    void diffEachSizeAgainstSamePart() {
        ReferenceGarment top = garment(GarmentPart.TOP, TOP_ID, "상의",
            Map.of("TOTAL_LENGTH", cm("40.0"), "CHEST_WIDTH", cm("34.0")));
        FitProduct product = product(TOP_ID, "상의", List.of(
            size("110", Map.of("TOTAL_LENGTH", cm("38.5"), "CHEST_WIDTH", cm("33.0"))),
            size("120", Map.of("TOTAL_LENGTH", cm("41.0"), "CHEST_WIDTH", cm("36.0")))));

        Optional<ReferenceFit> fit = ReferenceFit.compare(product, List.of(top));

        assertThat(fit).isPresent();
        assertThat(fit.get().productPart()).isEqualTo(GarmentPart.TOP);
        assertThat(fit.get().comparedPart()).isEqualTo(GarmentPart.TOP);
        assertThat(fit.get().substituted()).isFalse();
        assertThat(fit.get().items())
            .usingRecursiveFieldByFieldElementComparator(cmComparison())
            .containsExactly(
                new ReferenceFit.Item("TOTAL_LENGTH", cm("40.0"), List.of(
                    new ReferenceFit.SizeDiff("110", cm("38.5"), cm("-1.5")),
                    new ReferenceFit.SizeDiff("120", cm("41.0"), cm("1.0")))),
                new ReferenceFit.Item("CHEST_WIDTH", cm("34.0"), List.of(
                    new ReferenceFit.SizeDiff("110", cm("33.0"), cm("-1.0")),
                    new ReferenceFit.SizeDiff("120", cm("36.0"), cm("2.0")))));
    }

    // 상품 사이즈에는 모든 항목을 넣어서, 비교에서 빠지는 항목이 상품에 값이 없어서가 아니라 규칙 때문에 빠지는지 확인한다
    @ParameterizedTest
    @CsvSource({
        "54, 아우터, OUTER, TOP, CHEST_WIDTH SHOULDER_WIDTH",
        "55, 원피스, DRESS, TOP, CHEST_WIDTH SHOULDER_WIDTH",
        "56, 치마, SKIRT, BOTTOM, WAIST_WIDTH"})
    @DisplayName("FIT-01 · FIT-04 · FIT-05 아우터 · 원피스는 상의 칸, 치마는 바지 칸과 대신 비교하고 이때는 종류가 달라 너비 항목만 비교한다")
    void substitutePartComparesWidthOnly(Long categoryId, String categoryName, GarmentPart productPart,
        GarmentPart comparedPart, String expectedCodes) {
        ReferenceGarment top = garment(GarmentPart.TOP, TOP_ID, "상의",
            Map.of("TOTAL_LENGTH", cm("40.0"), "CHEST_WIDTH", cm("34.0"), "SHOULDER_WIDTH", cm("30.0"),
                "SLEEVE_LENGTH", cm("38.0")));
        ReferenceGarment bottom = garment(GarmentPart.BOTTOM, PANTS_ID, "바지",
            Map.of("TOTAL_LENGTH", cm("60.0"), "WAIST_WIDTH", cm("25.0"), "RISE", cm("20.0")));
        FitProduct product = product(categoryId, categoryName, List.of(size("120", everyItem())));

        Optional<ReferenceFit> fit = ReferenceFit.compare(product, List.of(top, bottom));

        assertThat(fit).isPresent();
        assertThat(fit.get().productPart()).isEqualTo(productPart);
        assertThat(fit.get().comparedPart()).isEqualTo(comparedPart);
        assertThat(fit.get().substituted()).isTrue();
        assertThat(fit.get().items()).extracting(ReferenceFit.Item::code)
            .containsExactly(expectedCodes.split(" "));
    }

    @Test
    @DisplayName("FIT-01 아우터 상품은 아우터 칸과 상의 칸이 모두 있으면 같은 종류인 아우터 칸과 비교한다")
    void samePartComesBeforeSubstitute() {
        ReferenceGarment top = garment(GarmentPart.TOP, TOP_ID, "상의",
            Map.of("TOTAL_LENGTH", cm("40.0"), "CHEST_WIDTH", cm("34.0")));
        ReferenceGarment outer = garment(GarmentPart.OUTER, OUTER_ID, "아우터",
            Map.of("TOTAL_LENGTH", cm("48.0"), "CHEST_WIDTH", cm("38.0")));
        FitProduct product = product(OUTER_ID, "아우터", List.of(
            size("120", Map.of("TOTAL_LENGTH", cm("50.0"), "CHEST_WIDTH", cm("39.5")))));

        Optional<ReferenceFit> fit = ReferenceFit.compare(product, List.of(top, outer));

        assertThat(fit).isPresent();
        assertThat(fit.get().productPart()).isEqualTo(GarmentPart.OUTER);
        assertThat(fit.get().comparedPart()).isEqualTo(GarmentPart.OUTER);
        assertThat(fit.get().substituted()).isFalse();
        assertThat(fit.get().items())
            .usingRecursiveFieldByFieldElementComparator(cmComparison())
            .containsExactly(
                new ReferenceFit.Item("TOTAL_LENGTH", cm("48.0"), List.of(
                    new ReferenceFit.SizeDiff("120", cm("50.0"), cm("2.0")))),
                new ReferenceFit.Item("CHEST_WIDTH", cm("38.0"), List.of(
                    new ReferenceFit.SizeDiff("120", cm("39.5"), cm("1.5")))));
    }

    // 「그 칸이 비면」은 그 칸에 기준 옷이 없다는 뜻이라, 기준 옷이 있는데 견줄 항목이 없을 때는 대신할 칸으로 넘어가지 않는다
    @Test
    @DisplayName("FIT-01 아우터 칸에 기준 옷이 있으면 견줄 항목이 없어도 상의 칸과 대신 비교하지 않는다")
    void noSubstituteWhenSamePartHasGarment() {
        // 상의 칸과 대신 비교했다면 어깨너비가 견줄 항목으로 나온다
        ReferenceGarment top = garment(GarmentPart.TOP, TOP_ID, "상의",
            Map.of("TOTAL_LENGTH", cm("40.0"), "CHEST_WIDTH", cm("34.0"), "SHOULDER_WIDTH", cm("30.0")));
        ReferenceGarment outer = garment(GarmentPart.OUTER, OUTER_ID, "아우터",
            Map.of("TOTAL_LENGTH", cm("48.0"), "CHEST_WIDTH", cm("38.0")));
        FitProduct product = product(OUTER_ID, "아우터", List.of(
            size("120", Map.of("SHOULDER_WIDTH", cm("32.0")))));

        assertThat(ReferenceFit.compare(product, List.of(top, outer))).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"52, 상의, BOTTOM, 60, 바지, WAIST_WIDTH", "60, 바지, TOP, 52, 상의, CHEST_WIDTH"})
    @DisplayName("FIT-01 상의 · 바지 상품은 자기 칸이 비면 대신할 칸이 없어 다른 칸이 있어도 기준 핏이 없다")
    void noSubstituteForTopAndPants(Long categoryId, String categoryName, GarmentPart otherPart,
        Long otherCategoryId, String otherCategoryName, String otherWidthCode) {
        ReferenceGarment other = garment(otherPart, otherCategoryId, otherCategoryName,
            Map.of("TOTAL_LENGTH", cm("40.0"), otherWidthCode, cm("30.0")));
        FitProduct product = product(categoryId, categoryName, List.of(size("120", everyItem())));

        assertThat(ReferenceFit.compare(product, List.of(other))).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"70, 잡화, 모자, false", "71, 잡화, 양말, false", "52, 의류, 상의, true"})
    @DisplayName("FIT-02 모자 · 양말 · 사이즈 무관 상품은 기준 옷이 있어도 기준 핏이 없다")
    void noFitForHatSocksAndSizeFree(Long categoryId, String rootName, String leafName, boolean sizeFree) {
        ReferenceGarment top = garment(GarmentPart.TOP, TOP_ID, "상의",
            Map.of("TOTAL_LENGTH", cm("40.0"), "CHEST_WIDTH", cm("34.0")));
        ReferenceGarment bottom = garment(GarmentPart.BOTTOM, PANTS_ID, "바지",
            Map.of("TOTAL_LENGTH", cm("60.0"), "WAIST_WIDTH", cm("25.0")));
        FitProduct product = new FitProduct(categoryId, List.of(rootName, leafName), sizeFree,
            List.of(size("FREE", everyItem())));

        assertThat(ReferenceFit.compare(product, List.of(top, bottom))).isEmpty();
    }

    @Test
    @DisplayName("FIT-03 기준 옷에 입력하지 않은 선택 항목(어깨너비 · 소매길이)은 상품에 값이 있어도 비교하지 않는다")
    void skipItemsMissingInGarment() {
        ReferenceGarment top = garment(GarmentPart.TOP, TOP_ID, "상의",
            Map.of("TOTAL_LENGTH", cm("40.0"), "CHEST_WIDTH", cm("34.0")));
        FitProduct product = product(TOP_ID, "상의", List.of(size("120", everyItem())));

        Optional<ReferenceFit> fit = ReferenceFit.compare(product, List.of(top));

        assertThat(fit).isPresent();
        assertThat(fit.get().items()).extracting(ReferenceFit.Item::code)
            .containsExactly("TOTAL_LENGTH", "CHEST_WIDTH");
    }

    // 「바지」가 이미 있어 「하의」의 이름을 바꾸지 못한 DB에서는 같은 바지 칸에 카테고리 ID가 둘이다
    @Test
    @DisplayName("FIT-05 같은 바지 칸이어도 기준 옷과 상품의 카테고리 ID가 다르면 길이 항목(총장 · 밑위)을 빼고 너비 항목만 비교한다")
    void sameLengthItemsOnlyForSameCategory() {
        ReferenceGarment bottom = garment(GarmentPart.BOTTOM, OLD_BOTTOM_ID, "하의",
            Map.of("TOTAL_LENGTH", cm("60.0"), "WAIST_WIDTH", cm("25.0"), "RISE", cm("20.0")));
        FitProduct product = product(PANTS_ID, "바지", List.of(size("120", everyItem())));

        Optional<ReferenceFit> fit = ReferenceFit.compare(product, List.of(bottom));

        assertThat(fit).isPresent();
        assertThat(fit.get().productPart()).isEqualTo(GarmentPart.BOTTOM);
        assertThat(fit.get().comparedPart()).isEqualTo(GarmentPart.BOTTOM);
        assertThat(fit.get().items()).extracting(ReferenceFit.Item::code).containsExactly("WAIST_WIDTH");
    }

    @Test
    @DisplayName("FIT-03 상품의 일부 사이즈에만 있는 항목은 값이 있는 사이즈만 비교한다")
    void skipSizesMissingTheItem() {
        ReferenceGarment top = garment(GarmentPart.TOP, TOP_ID, "상의",
            Map.of("TOTAL_LENGTH", cm("40.0"), "CHEST_WIDTH", cm("34.0"), "SHOULDER_WIDTH", cm("30.0")));
        FitProduct product = product(TOP_ID, "상의", List.of(
            size("110", Map.of("TOTAL_LENGTH", cm("38.0"), "CHEST_WIDTH", cm("33.0"))),
            size("120", Map.of("TOTAL_LENGTH", cm("41.0"), "CHEST_WIDTH", cm("36.0"),
                "SHOULDER_WIDTH", cm("31.0")))));

        Optional<ReferenceFit> fit = ReferenceFit.compare(product, List.of(top));

        assertThat(fit).isPresent();
        assertThat(fit.get().items())
            .usingRecursiveFieldByFieldElementComparator(cmComparison())
            .containsExactly(
                new ReferenceFit.Item("TOTAL_LENGTH", cm("40.0"), List.of(
                    new ReferenceFit.SizeDiff("110", cm("38.0"), cm("-2.0")),
                    new ReferenceFit.SizeDiff("120", cm("41.0"), cm("1.0")))),
                new ReferenceFit.Item("CHEST_WIDTH", cm("34.0"), List.of(
                    new ReferenceFit.SizeDiff("110", cm("33.0"), cm("-1.0")),
                    new ReferenceFit.SizeDiff("120", cm("36.0"), cm("2.0")))),
                new ReferenceFit.Item("SHOULDER_WIDTH", cm("30.0"), List.of(
                    new ReferenceFit.SizeDiff("120", cm("31.0"), cm("1.0")))));
    }

    @ParameterizedTest
    @MethodSource("sizesWithoutCommonItem")
    @DisplayName("FIT-03 칸은 있지만 기준 옷과 상품 양쪽에 값이 있는 항목이 하나도 없으면 기준 핏이 없다")
    void noFitWithoutCommonItem(List<FitProduct.SizeMeasurements> sizes) {
        ReferenceGarment top = garment(GarmentPart.TOP, TOP_ID, "상의",
            Map.of("TOTAL_LENGTH", cm("40.0"), "CHEST_WIDTH", cm("34.0")));
        FitProduct product = product(TOP_ID, "상의", sizes);

        assertThat(ReferenceFit.compare(product, List.of(top))).isEmpty();
    }

    // 실측 줄이 없는 상품과, 실측 줄은 있지만 기준 옷에 없는 항목만 잰 상품이다
    private static List<List<FitProduct.SizeMeasurements>> sizesWithoutCommonItem() {
        return List.of(
            List.of(),
            List.of(size("120", Map.of("SHOULDER_WIDTH", cm("31.0"), "SLEEVE_LENGTH", cm("40.0")))));
    }

    // 칸마다 받는 실측 항목을 모두 입력한 기준 옷이다
    @ParameterizedTest
    @CsvSource({
        "TOP, 52, 상의, TOTAL_LENGTH CHEST_WIDTH SHOULDER_WIDTH SLEEVE_LENGTH",
        "BOTTOM, 60, 바지, TOTAL_LENGTH WAIST_WIDTH RISE",
        "OUTER, 54, 아우터, TOTAL_LENGTH CHEST_WIDTH SHOULDER_WIDTH SLEEVE_LENGTH",
        "DRESS, 55, 원피스, TOTAL_LENGTH CHEST_WIDTH SHOULDER_WIDTH",
        "SKIRT, 56, 치마, TOTAL_LENGTH WAIST_WIDTH HIP_WIDTH"})
    @DisplayName("FIT-03 · FIT-05 같은 칸 · 같은 카테고리면 입력한 선택 항목과 길이 항목까지 모두 비교한다")
    void sameCategoryComparesEveryEnteredItem(GarmentPart part, Long categoryId, String categoryName,
        String codes) {
        Map<String, BigDecimal> measurements = new HashMap<>();
        for (String code : codes.split(" ")) {
            measurements.put(code, cm("30.0"));
        }
        ReferenceGarment garment = garment(part, categoryId, categoryName, measurements);
        FitProduct product = product(categoryId, categoryName, List.of(size("120", everyItem())));

        Optional<ReferenceFit> fit = ReferenceFit.compare(product, List.of(garment));

        assertThat(fit).isPresent();
        assertThat(fit.get().comparedPart()).isEqualTo(part);
        assertThat(fit.get().items()).extracting(ReferenceFit.Item::code).containsExactly(codes.split(" "));
    }

    private static Map<String, BigDecimal> everyItem() {
        return Map.of("TOTAL_LENGTH", cm("50.0"), "CHEST_WIDTH", cm("36.0"), "SHOULDER_WIDTH", cm("31.0"),
            "SLEEVE_LENGTH", cm("40.0"), "WAIST_WIDTH", cm("26.0"), "HIP_WIDTH", cm("33.0"), "RISE", cm("21.0"));
    }

    // 40과 40.0처럼 소수 자리만 다른 값은 같은 cm로 본다
    private static RecursiveComparisonConfiguration cmComparison() {
        return RecursiveComparisonConfiguration.builder()
            .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
            .build();
    }

    private static ReferenceGarment garment(GarmentPart part, Long categoryId, String categoryName,
        Map<String, BigDecimal> measurements) {
        return ReferenceGarment.create(CHILD_ID, ReferenceGarmentSpec.of(part, categoryId,
            List.of(new GarmentCategoryPathEntry(CLOTHING_ID, "의류"),
                new GarmentCategoryPathEntry(categoryId, categoryName)),
            measurements));
    }

    private static FitProduct product(Long categoryId, String categoryName, List<FitProduct.SizeMeasurements> sizes) {
        return new FitProduct(categoryId, List.of("의류", categoryName), false, sizes);
    }

    private static FitProduct.SizeMeasurements size(String size, Map<String, BigDecimal> measurements) {
        return new FitProduct.SizeMeasurements(size, measurements);
    }

    private static BigDecimal cm(String value) {
        return new BigDecimal(value);
    }
}
