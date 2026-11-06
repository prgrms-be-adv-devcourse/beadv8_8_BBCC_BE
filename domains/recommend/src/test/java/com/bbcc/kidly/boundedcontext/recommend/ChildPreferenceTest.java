package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.domain.preference.ChildPreference;
import com.bbcc.kidly.boundedcontext.recommend.domain.preference.PreferenceActivity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ChildPreferenceTest {

    private static final Long CATEGORY_ID = 52L;

    private static final String BRAND = "키들리";

    private static final Long OTHER_CATEGORY_ID = 99L;

    private static final String OTHER_BRAND = "다른브랜드";

    @ParameterizedTest(name = "상세 진입 {0}번")
    @ValueSource(ints = {1, 3})
    @DisplayName("PREF-02 활동에 나온 카테고리는 나온 횟수와 상관없이 3점이다")
    void categoryInActivityGetsThreePoints(int times) {
        ChildPreference preference = ChildPreference.empty();
        for (int i = 0; i < times; i++) {
            preference = preference.plus(new PreferenceActivity(CATEGORY_ID, BRAND, false));
        }

        assertThat(preference.categoryPoints(CATEGORY_ID)).isEqualTo(3);
    }

    @ParameterizedTest(name = "활동 카테고리 {0}, 상품 카테고리 {1}")
    @CsvSource({
        "52, 99",
        ",",
    })
    @DisplayName("PREF-02 활동에 나오지 않은 카테고리와 카테고리가 없는 활동은 0점이다")
    void categoryNotInActivityGetsZeroPoints(Long activityCategoryId, Long productCategoryId) {
        ChildPreference preference = ChildPreference.empty()
            .plus(new PreferenceActivity(activityCategoryId, BRAND, false));

        assertThat(preference.categoryPoints(productCategoryId)).isZero();
    }

    @Test
    @DisplayName("PREF-03 장바구니 담기에 나온 카테고리는 상세 진입과 섞여도 5점이다")
    void categoryInCartAddGetsFivePoints() {
        ChildPreference preference = ChildPreference.empty()
            .plus(new PreferenceActivity(CATEGORY_ID, BRAND, false))
            .plus(new PreferenceActivity(CATEGORY_ID, BRAND, true));

        assertThat(preference.categoryPoints(CATEGORY_ID)).isEqualTo(5);
    }

    @ParameterizedTest(name = "활동 브랜드 {0}, 상품 브랜드 {1} → {2}점")
    @CsvSource({
        "키들리, 키들리, 2",
        "키들리, 다른브랜드, 0",
        ", , 0",
        "'', '', 0",
        "'  ', '  ', 0",
    })
    @DisplayName("PREF-04 활동에 나온 브랜드는 2점, 나오지 않은 브랜드와 브랜드가 없거나 빈 상품은 0점이다")
    void brandPoints(String activityBrand, String productBrand, int expected) {
        ChildPreference preference = ChildPreference.empty()
            .plus(new PreferenceActivity(CATEGORY_ID, activityBrand, false));

        assertThat(preference.brandPoints(productBrand)).isEqualTo(expected);
    }

    @Test
    @DisplayName("PREF-07 뺀 장바구니 담기는 그 담기가 준 카테고리 · 담기 · 브랜드 몫이 모두 빠진다")
    void removedCartAddLosesAllItsPoints() {
        PreferenceActivity cartAdd = new PreferenceActivity(CATEGORY_ID, BRAND, true);

        ChildPreference preference = ChildPreference.empty().plus(cartAdd).minus(cartAdd);

        assertThat(preference.categoryPoints(CATEGORY_ID)).isZero();
        assertThat(preference.brandPoints(BRAND)).isZero();
    }

    @Test
    @DisplayName("PREF-07 담기를 빼도 같은 카테고리 · 브랜드의 다른 활동이 남아 있으면 3점 · 2점은 남는다")
    void removedCartAddKeepsPointsOfOtherActivities() {
        PreferenceActivity cartAdd = new PreferenceActivity(CATEGORY_ID, BRAND, true);
        PreferenceActivity detailView = new PreferenceActivity(CATEGORY_ID, BRAND, false);

        ChildPreference preference = ChildPreference.empty().plus(detailView).plus(cartAdd).minus(cartAdd);

        assertThat(preference.categoryPoints(CATEGORY_ID)).isEqualTo(3);
        assertThat(preference.brandPoints(BRAND)).isEqualTo(2);
    }

    @ParameterizedTest(name = "빼기를 먼저 {0}")
    @ValueSource(booleans = {false, true})
    @DisplayName("PREF-01 기준 시각 뒤 활동을 더했다가 빼면 순서와 상관없이 그 활동이 없던 집계와 점수가 같다")
    void activityAfterBaseTimeIsReverted(boolean minusFirst) {
        ChildPreference beforeBaseTime = ChildPreference.empty()
            .plus(new PreferenceActivity(CATEGORY_ID, BRAND, false));
        PreferenceActivity afterBaseTime = new PreferenceActivity(OTHER_CATEGORY_ID, OTHER_BRAND, true);

        ChildPreference reverted = minusFirst
            ? beforeBaseTime.minus(afterBaseTime).plus(afterBaseTime)
            : beforeBaseTime.plus(afterBaseTime).minus(afterBaseTime);

        assertThat(reverted.categoryPoints(CATEGORY_ID)).isEqualTo(3);
        assertThat(reverted.brandPoints(BRAND)).isEqualTo(2);
        assertThat(reverted.categoryPoints(OTHER_CATEGORY_ID)).isZero();
        assertThat(reverted.brandPoints(OTHER_BRAND)).isZero();
    }
}
