package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.domain.recommended.ChildActivityEvidence;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 자녀 활동으로 홈 맛보기 후보마다 이유의 근거 라벨을 만드는 규칙을 확인한다.
 * 기간(본 · 담은 활동 30일, 구매 3개월)은 부르는 쪽이 자른 값을 넘긴다.
 */
class ChildActivityEvidenceTest {

    private static final Long PANTS_ID = 60L;
    private static final String PANTS = "바지";
    private static final String BRAND = "데님하우스";

    @ParameterizedTest
    @CsvSource({"4, false", "5, true", "12, true"})
    @DisplayName("HOME-05 30일 안에 5번 이상 본 카테고리면 「자주 본 카테고리」 근거를 붙인다")
    void frequentlyViewedCategory(int activityCount, boolean expected) {
        ChildActivityEvidence evidence = new ChildActivityEvidence(Map.of(PANTS_ID, activityCount), Map.of(),
            Map.of(), Set.of(), Set.of());

        boolean labeled = evidence.labelsFor(PANTS_ID, PANTS, null).contains("자주 본 카테고리: 바지");

        assertThat(labeled).isEqualTo(expected);
    }

    @Test
    @DisplayName("HOME-05 30일 안에 장바구니에 담은 카테고리면 「장바구니에 담은 카테고리」 근거를 붙인다")
    void cartAddedCategory() {
        ChildActivityEvidence evidence = new ChildActivityEvidence(Map.of(), Map.of(PANTS_ID, 1), Map.of(), Set.of(),
            Set.of());

        assertThat(evidence.labelsFor(PANTS_ID, PANTS, null)).containsExactly("장바구니에 담은 카테고리: 바지");
    }

    @ParameterizedTest
    @CsvSource({"4, false", "5, true"})
    @DisplayName("HOME-05 30일 안에 5번 이상 본 브랜드면 「자주 본 브랜드」 근거를 붙인다")
    void frequentlyViewedBrand(int activityCount, boolean expected) {
        ChildActivityEvidence evidence = new ChildActivityEvidence(Map.of(), Map.of(), Map.of(BRAND, activityCount),
            Set.of(), Set.of());

        assertThat(evidence.labelsFor(PANTS_ID, PANTS, BRAND).contains("자주 본 브랜드: 데님하우스")).isEqualTo(expected);
    }

    @Test
    @DisplayName("HOME-05 브랜드가 없는 후보에는 브랜드 근거를 붙이지 않는다")
    void noBrandLabelWithoutBrand() {
        ChildActivityEvidence evidence = new ChildActivityEvidence(Map.of(), Map.of(), Map.of(BRAND, 9),
            Set.of(BRAND), Set.of());

        assertThat(evidence.labelsFor(PANTS_ID, PANTS, null)).isEmpty();
    }

    @Test
    @DisplayName("HOME-05 3개월 안에 산 상품의 브랜드 · 카테고리면 「산 적 있는 브랜드」 · 「전에 산 카테고리」 근거를 붙인다")
    void purchasedBrandAndCategory() {
        ChildActivityEvidence evidence = new ChildActivityEvidence(Map.of(), Map.of(), Map.of(), Set.of(BRAND),
            Set.of(PANTS_ID));

        assertThat(evidence.labelsFor(PANTS_ID, PANTS, BRAND))
            .containsExactly("산 적 있는 브랜드: 데님하우스", "전에 산 카테고리: 바지");
    }

    @Test
    @DisplayName("HOME-05 근거가 여러 개면 자주 본 근거를 활동 횟수가 많은 순으로 먼저 두고, 담은 · 산 근거를 뒤에 둔다")
    void orderFrequentByCountThenCartAndPurchase() {
        ChildActivityEvidence evidence = new ChildActivityEvidence(Map.of(PANTS_ID, 6), Map.of(PANTS_ID, 2),
            Map.of(BRAND, 9), Set.of(BRAND), Set.of(PANTS_ID));

        assertThat(evidence.labelsFor(PANTS_ID, PANTS, BRAND)).containsExactly(
            "자주 본 브랜드: 데님하우스", "자주 본 카테고리: 바지", "장바구니에 담은 카테고리: 바지",
            "산 적 있는 브랜드: 데님하우스", "전에 산 카테고리: 바지");
    }

    @Test
    @DisplayName("HOME-05 맞는 근거가 없으면 빈 목록이다")
    void emptyWithoutMatchingActivity() {
        ChildActivityEvidence evidence = new ChildActivityEvidence(Map.of(99L, 9), Map.of(99L, 1),
            Map.of("다른 브랜드", 9), Set.of("다른 브랜드"), Set.of(99L));

        assertThat(evidence.labelsFor(PANTS_ID, PANTS, BRAND)).isEmpty();
    }
}
