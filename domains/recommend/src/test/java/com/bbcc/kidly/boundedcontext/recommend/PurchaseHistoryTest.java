package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.domain.purchase.PurchaseFit;
import com.bbcc.kidly.boundedcontext.recommend.domain.purchase.PurchaseHistory;
import com.bbcc.kidly.boundedcontext.recommend.domain.purchase.PurchasedGarment;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.FitProduct;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.ReferenceFit;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PurchaseHistoryTest {

    private static final Long TOP_ID = 52L;

    // 같은 상의 칸이지만 세부 카테고리가 다른 옷이다
    private static final Long OTHER_TOP_ID = 53L;

    private static final Long PANTS_ID = 60L;

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 12);

    private static final BigDecimal CM_PER_MONTH = new BigDecimal("0.50");

    @Test
    @DisplayName("PUR-04 같은 카테고리의 옷을 샀으면 사이즈마다 지금 상품 실측에서 그때 산 옷 실측을 뺀 cm를 길이 · 너비 항목 모두 구한다")
    void diffEachSizeAgainstPurchasedGarmentOfSameCategory() {
        FitProduct product = product(TOP_ID, "상의", List.of(
            size("110", Map.of("TOTAL_LENGTH", cm("42.0"), "CHEST_WIDTH", cm("34.0"))),
            size("120", Map.of("TOTAL_LENGTH", cm("45.0"), "CHEST_WIDTH", cm("37.0")))));
        PurchasedGarment bought = purchased(1L, "110", TODAY.minusMonths(5).atTime(10, 0), TOP_ID, "상의",
            "키들리", Map.of("TOTAL_LENGTH", cm("43.0"), "CHEST_WIDTH", cm("33.0")));

        PurchaseHistory history = PurchaseHistory.of(
            new PurchaseHistory.CompareInfo(product, "키들리", List.of(bought), CM_PER_MONTH, TODAY));

        assertThat(history.purchases()).hasSize(1);
        assertThat(history.purchases().getFirst().items())
            .usingRecursiveFieldByFieldElementComparator(cmComparison())
            .containsExactly(
                new ReferenceFit.Item("TOTAL_LENGTH", cm("43.0"), List.of(
                    new ReferenceFit.SizeDiff("110", cm("42.0"), cm("-1.0")),
                    new ReferenceFit.SizeDiff("120", cm("45.0"), cm("2.0")))),
                new ReferenceFit.Item("CHEST_WIDTH", cm("33.0"), List.of(
                    new ReferenceFit.SizeDiff("110", cm("34.0"), cm("1.0")),
                    new ReferenceFit.SizeDiff("120", cm("37.0"), cm("4.0")))));
    }

    @Test
    @DisplayName("PUR-04 다른 카테고리의 옷을 샀으면 길이 항목은 빼고 너비 항목만 비교한다")
    void compareWidthOnlyAgainstPurchasedGarmentOfOtherCategory() {
        FitProduct product = product(TOP_ID, "상의", List.of(
            size("120", Map.of("TOTAL_LENGTH", cm("45.0"), "CHEST_WIDTH", cm("37.0"),
                "SLEEVE_LENGTH", cm("41.0")))));
        PurchasedGarment bought = purchased(1L, "110", TODAY.minusMonths(5).atTime(10, 0), OTHER_TOP_ID, "상의",
            "키들리", Map.of("TOTAL_LENGTH", cm("43.0"), "CHEST_WIDTH", cm("33.0"), "SLEEVE_LENGTH", cm("38.0")));

        PurchaseHistory history = PurchaseHistory.of(
            new PurchaseHistory.CompareInfo(product, "키들리", List.of(bought), CM_PER_MONTH, TODAY));

        assertThat(history.purchases().getFirst().items())
            .usingRecursiveFieldByFieldElementComparator(cmComparison())
            .containsExactly(
                new ReferenceFit.Item("CHEST_WIDTH", cm("33.0"), List.of(
                    new ReferenceFit.SizeDiff("120", cm("37.0"), cm("4.0")))));
    }

    // 확정일에 하루를 더하면 달력으로 한 달이 덜 지난 것이다
    @ParameterizedTest
    @CsvSource({
        "키들리, 키들리, 0.50, 0, true, 5, 2.5",
        "키들리, 다른 브랜드, 0.37, 0, false, 5, 1.9",
        ", 키들리, 0.50, 0, false, 5, 2.5",
        "키들리, , 0.50, 1, false, 4, 2.0",
        ", , 0.50, 0, false, 5, 2.5"})
    @DisplayName("PUR-03 구매마다 같은 브랜드인지(브랜드가 없으면 같지 않음), 산 뒤 지난 달력 개월 수, 그동안 예상 성장량(성장 속도 × 개월, 소수 첫째 자리 반올림)을 구한다")
    void describeEachPurchase(String productBrand, String boughtBrand, String cmPerMonth, int extraDays,
        boolean sameBrand, int monthsAgo, String expectedGrowthCm) {
        FitProduct product = product(TOP_ID, "상의", List.of(size("120", Map.of("CHEST_WIDTH", cm("37.0")))));
        LocalDateTime confirmedAt = TODAY.minusMonths(5).plusDays(extraDays).atTime(10, 0);
        PurchasedGarment bought = purchased(1L, "110", confirmedAt, TOP_ID, "상의", boughtBrand,
            Map.of("CHEST_WIDTH", cm("33.0")));

        PurchaseHistory history = PurchaseHistory.of(
            new PurchaseHistory.CompareInfo(product, productBrand, List.of(bought), cm(cmPerMonth), TODAY));

        PurchaseFit fit = history.purchases().getFirst();
        assertThat(fit.size()).isEqualTo("110");
        assertThat(fit.sameBrand()).isEqualTo(sameBrand);
        assertThat(fit.monthsAgo()).isEqualTo(monthsAgo);
        assertThat(fit.expectedGrowthCm()).isEqualByComparingTo(expectedGrowthCm);
    }

    // 바지 구매가 가장 최근이고, 상의 넷 중 둘은 확정 시각이 같다
    @Test
    @DisplayName("PUR-02 지금 상품과 같은 칸의 구매만 확정 시각이 늦은 순(같으면 구매 기록 ID가 큰 순)으로 최대 3개 고른다")
    void pickLatestThreePurchasesOfSamePart() {
        FitProduct product = product(TOP_ID, "상의", List.of(size("120", Map.of("CHEST_WIDTH", cm("37.0")))));
        LocalDateTime sameTime = TODAY.minusMonths(2).atTime(10, 0);
        List<PurchasedGarment> garments = List.of(
            purchased(1L, "100", TODAY.minusMonths(9).atTime(10, 0), TOP_ID, "상의", "키들리", Map.of()),
            purchased(2L, "110", TODAY.minusMonths(6).atTime(10, 0), TOP_ID, "상의", "키들리", Map.of()),
            purchased(3L, "115", sameTime, TOP_ID, "상의", "키들리", Map.of()),
            purchased(4L, "120", sameTime, TOP_ID, "상의", "키들리", Map.of()),
            purchased(5L, "130", TODAY.minusMonths(1).atTime(10, 0), PANTS_ID, "바지", "키들리", Map.of()));

        PurchaseHistory history = PurchaseHistory.of(
            new PurchaseHistory.CompareInfo(product, "키들리", garments, CM_PER_MONTH, TODAY));

        assertThat(history.purchases()).extracting(PurchaseFit::size).containsExactly("120", "115", "110");
    }

    @Test
    @DisplayName("PUR-01 지난 구매 사이즈는 다른 칸 구매가 더 최근이어도 지금 상품과 같은 칸에서 마지막으로 산 사이즈다")
    void lastPurchasedSizeIsLatestOfSamePart() {
        FitProduct product = product(TOP_ID, "상의", List.of(size("120", Map.of("CHEST_WIDTH", cm("37.0")))));
        List<PurchasedGarment> garments = List.of(
            purchased(1L, "110", TODAY.minusMonths(6).atTime(10, 0), TOP_ID, "상의", "키들리", Map.of()),
            purchased(2L, "115", TODAY.minusMonths(2).atTime(10, 0), OTHER_TOP_ID, "상의", "다른 브랜드", Map.of()),
            purchased(3L, "130", TODAY.minusMonths(1).atTime(10, 0), PANTS_ID, "바지", "키들리", Map.of()));

        PurchaseHistory history = PurchaseHistory.of(
            new PurchaseHistory.CompareInfo(product, "키들리", garments, CM_PER_MONTH, TODAY));

        assertThat(history.lastPurchasedSize()).isEqualTo("115");
    }

    @Test
    @DisplayName("PUR-01 · PUR-02 같은 칸의 구매가 없으면 지난 구매 사이즈는 null이고 AI에 넘길 구매는 빈 목록이다")
    void noPurchaseOfSamePart() {
        FitProduct product = product(TOP_ID, "상의", List.of(size("120", Map.of("CHEST_WIDTH", cm("37.0")))));
        PurchasedGarment pants = purchased(1L, "130", TODAY.minusMonths(1).atTime(10, 0), PANTS_ID, "바지", "키들리",
            Map.of());

        PurchaseHistory history = PurchaseHistory.of(
            new PurchaseHistory.CompareInfo(product, "키들리", List.of(pants), CM_PER_MONTH, TODAY));

        assertThat(history.lastPurchasedSize()).isNull();
        assertThat(history.purchases()).isEmpty();
    }

    @Test
    @DisplayName("PUR-04 산 사이즈의 실측이 없으면 그 구매는 빼지 않고 실측 차이만 빈 목록이다")
    void keepPurchaseWithoutMeasurements() {
        FitProduct product = product(TOP_ID, "상의", List.of(size("120", Map.of("CHEST_WIDTH", cm("37.0")))));
        PurchasedGarment bought = purchased(1L, "110", TODAY.minusMonths(5).atTime(10, 0), TOP_ID, "상의", "키들리",
            Map.of());

        PurchaseHistory history = PurchaseHistory.of(
            new PurchaseHistory.CompareInfo(product, "키들리", List.of(bought), CM_PER_MONTH, TODAY));

        assertThat(history.purchases()).hasSize(1);
        assertThat(history.purchases().getFirst().size()).isEqualTo("110");
        assertThat(history.purchases().getFirst().items()).isEmpty();
    }

    // 사이즈 무관 상품도 상의 카테고리라 칸은 있다. 모자는 맞는 칸이 없다
    @ParameterizedTest
    @CsvSource({"상의, true", "모자, false"})
    @DisplayName("PUR-01 · PUR-02 사이즈 무관 상품이나 칸이 없는 상품(모자 · 양말)은 지난 구매를 쓰지 않는다")
    void skipPurchasesForSizeFreeOrPartlessProduct(String categoryName, boolean sizeFree) {
        FitProduct product = new FitProduct(TOP_ID, List.of("의류", categoryName), sizeFree,
            List.of(size("120", Map.of("CHEST_WIDTH", cm("37.0")))));
        PurchasedGarment bought = purchased(1L, "110", TODAY.minusMonths(5).atTime(10, 0), TOP_ID, categoryName,
            "키들리", Map.of("CHEST_WIDTH", cm("33.0")));

        PurchaseHistory history = PurchaseHistory.of(
            new PurchaseHistory.CompareInfo(product, "키들리", List.of(bought), CM_PER_MONTH, TODAY));

        assertThat(history.lastPurchasedSize()).isNull();
        assertThat(history.purchases()).isEmpty();
    }

    private static PurchasedGarment purchased(Long purchaseRecordId, String size, LocalDateTime confirmedAt,
        Long categoryId, String categoryName, String brand, Map<String, BigDecimal> measurements) {
        return new PurchasedGarment(purchaseRecordId, size, confirmedAt, categoryId, List.of("의류", categoryName),
            brand, measurements);
    }

    private static FitProduct product(Long categoryId, String categoryName, List<FitProduct.SizeMeasurements> sizes) {
        return new FitProduct(categoryId, List.of("의류", categoryName), false, sizes);
    }

    private static FitProduct.SizeMeasurements size(String size, Map<String, BigDecimal> measurements) {
        return new FitProduct.SizeMeasurements(size, measurements);
    }

    private static RecursiveComparisonConfiguration cmComparison() {
        return RecursiveComparisonConfiguration.builder()
            .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
            .build();
    }

    private static BigDecimal cm(String value) {
        return new BigDecimal(value);
    }
}
