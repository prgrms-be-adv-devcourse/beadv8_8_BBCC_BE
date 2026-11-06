package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.CHILD_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_BRAND;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_CATEGORY_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.recommend.domain.preference.ChildBrandPreference;
import com.bbcc.kidly.boundedcontext.recommend.domain.preference.ChildCategoryPreference;
import com.bbcc.kidly.boundedcontext.recommend.domain.preference.ChildPreference;
import com.bbcc.kidly.boundedcontext.recommend.domain.preference.ChildPreferenceRepository;
import com.bbcc.kidly.boundedcontext.recommend.domain.preference.PreferenceActivity;
import com.bbcc.kidly.boundedcontext.recommend.out.preference.JpaChildBrandPreferenceRepository;
import com.bbcc.kidly.boundedcontext.recommend.out.preference.JpaChildCategoryPreferenceRepository;
import com.bbcc.kidly.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 자녀별 선호 집계를 실제 DB에 올리고 내리고 읽는 규칙을 확인한다. 리포지토리는 트랜잭션을 열지 않으므로 쓰기는 TransactionTemplate으로 감싼다.
 */
@IntegrationTest
class ChildPreferenceRepositoryIntegrationTest {

    private static final Long OTHER_CHILD_ID = 12L;

    private static final Long NO_ACTIVITY_CHILD_ID = 13L;

    private static final Long OTHER_CATEGORY_ID = 99L;

    private static final String OTHER_BRAND = "다른브랜드";

    @Autowired
    private ChildPreferenceRepository childPreferenceRepository;

    // 저장된 줄의 횟수를 그대로 읽으려고 Spring Data 리포지토리를 쓴다
    @Autowired
    private JpaChildCategoryPreferenceRepository categoryPreferences;

    @Autowired
    private JpaChildBrandPreferenceRepository brandPreferences;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @ParameterizedTest(name = "담기 {0} → 카테고리 {1}점")
    @CsvSource({
        "false, 3",
        "true, 5",
    })
    @DisplayName("PREF-02 · 03 · 04 첫 활동을 더한 뒤 읽으면 그 카테고리는 3점(담기면 5점), 그 브랜드는 2점이다")
    void readPreferenceAfterFirstActivity(boolean cartAdd, int expectedCategoryPoints) {
        add(CHILD_1_ID, new PreferenceActivity(PRODUCT_A_CATEGORY_ID, PRODUCT_A_BRAND, cartAdd));

        ChildPreference preference = childPreferenceRepository.findByChildProfileId(CHILD_1_ID);

        assertThat(preference.categoryPoints(PRODUCT_A_CATEGORY_ID)).isEqualTo(expectedCategoryPoints);
        assertThat(preference.brandPoints(PRODUCT_A_BRAND)).isEqualTo(2);
    }

    @ParameterizedTest(name = "카테고리 {0}, 브랜드 [{1}] → 카테고리 줄 {2}개, 브랜드 줄 {3}개")
    @CsvSource(value = {
        "52, 키들리, 1, 1",
        ", 키들리, 0, 1",
        "52, , 1, 0",
        "52, '', 1, 0",
        "52, '  ', 1, 0",
        ", , 0, 0",
    })
    @DisplayName("PREF-04 카테고리가 없는 활동은 카테고리 줄을, 브랜드가 null이거나 빈 · 공백인 활동은 브랜드 줄을 만들지 않는다")
    void createNoRowForMissingCategoryOrBrand(Long categoryId, String brand, int categoryRows, int brandRows) {
        add(CHILD_1_ID, new PreferenceActivity(categoryId, brand, false));

        assertThat(categoryPreferences.findAllByChildProfileId(CHILD_1_ID)).hasSize(categoryRows);
        assertThat(brandPreferences.findAllByChildProfileId(CHILD_1_ID)).hasSize(brandRows);
    }

    @Test
    @DisplayName("PREF-07 같은 활동을 두 번 더하면 카테고리 · 브랜드 줄은 하나씩이고 횟수는 2다")
    void keepOneRowWhenSameActivityAddedTwice() {
        PreferenceActivity activity = new PreferenceActivity(PRODUCT_A_CATEGORY_ID, PRODUCT_A_BRAND, true);

        add(CHILD_1_ID, activity);
        add(CHILD_1_ID, activity);

        assertThat(categoryPreferences.findAllByChildProfileId(CHILD_1_ID)).singleElement()
            .extracting(ChildCategoryPreference::getCategoryId, ChildCategoryPreference::getActivityCount,
                ChildCategoryPreference::getCartAddCount)
            .containsExactly(PRODUCT_A_CATEGORY_ID, 2, 2);
        assertThat(brandPreferences.findAllByChildProfileId(CHILD_1_ID)).singleElement()
            .extracting(ChildBrandPreference::getBrand, ChildBrandPreference::getActivityCount)
            .containsExactly(PRODUCT_A_BRAND, 2);
    }

    @Test
    @DisplayName("PREF-07 두 번 더한 활동을 한 번 빼면 횟수가 1이 되어 카테고리 3점 · 브랜드 2점은 그대로 남는다")
    void keepPointsWhenOneOfTwoActivitiesSubtracted() {
        PreferenceActivity activity = new PreferenceActivity(PRODUCT_A_CATEGORY_ID, PRODUCT_A_BRAND, false);
        add(CHILD_1_ID, activity);
        add(CHILD_1_ID, activity);

        subtract(CHILD_1_ID, activity);

        assertThat(categoryPreferences.findAllByChildProfileId(CHILD_1_ID)).singleElement()
            .extracting(ChildCategoryPreference::getActivityCount)
            .isEqualTo(1);
        ChildPreference preference = childPreferenceRepository.findByChildProfileId(CHILD_1_ID);
        assertThat(preference.categoryPoints(PRODUCT_A_CATEGORY_ID)).isEqualTo(3);
        assertThat(preference.brandPoints(PRODUCT_A_BRAND)).isEqualTo(2);
    }

    @Test
    @DisplayName("PREF-07 담기를 빼면 카테고리 · 담기 · 브랜드 몫이 모두 빠져 횟수가 0이 되고 점수는 나오지 않는다")
    void subtractCartAddRemovesAllOfItsShares() {
        PreferenceActivity cartAdd = new PreferenceActivity(PRODUCT_A_CATEGORY_ID, PRODUCT_A_BRAND, true);
        add(CHILD_1_ID, cartAdd);

        subtract(CHILD_1_ID, cartAdd);

        assertThat(categoryPreferences.findAllByChildProfileId(CHILD_1_ID)).singleElement()
            .extracting(ChildCategoryPreference::getActivityCount, ChildCategoryPreference::getCartAddCount)
            .containsExactly(0, 0);
        assertThat(brandPreferences.findAllByChildProfileId(CHILD_1_ID)).singleElement()
            .extracting(ChildBrandPreference::getActivityCount)
            .isEqualTo(0);
        ChildPreference preference = childPreferenceRepository.findByChildProfileId(CHILD_1_ID);
        assertThat(preference.categoryPoints(PRODUCT_A_CATEGORY_ID)).isZero();
        assertThat(preference.brandPoints(PRODUCT_A_BRAND)).isZero();
    }

    @Test
    @DisplayName("PREF-07 줄이 없는데 빼면 횟수가 음수인 줄이 생기고, 한 번 더해도 0에서 자르지 않아 나오지 않은 것으로 본다")
    void subtractBeforeAddLeavesNegativeCountsWithoutClamping() {
        PreferenceActivity cartAdd = new PreferenceActivity(PRODUCT_A_CATEGORY_ID, PRODUCT_A_BRAND, true);

        subtract(CHILD_1_ID, cartAdd);

        assertThat(categoryPreferences.findAllByChildProfileId(CHILD_1_ID)).singleElement()
            .extracting(ChildCategoryPreference::getActivityCount, ChildCategoryPreference::getCartAddCount)
            .containsExactly(-1, -1);
        assertThat(brandPreferences.findAllByChildProfileId(CHILD_1_ID)).singleElement()
            .extracting(ChildBrandPreference::getActivityCount)
            .isEqualTo(-1);

        add(CHILD_1_ID, new PreferenceActivity(PRODUCT_A_CATEGORY_ID, PRODUCT_A_BRAND, false));

        ChildPreference preference = childPreferenceRepository.findByChildProfileId(CHILD_1_ID);
        assertThat(preference.categoryPoints(PRODUCT_A_CATEGORY_ID)).isZero();
        assertThat(preference.brandPoints(PRODUCT_A_BRAND)).isZero();
    }

    @Test
    @DisplayName("PREF-02 · 04 다른 자녀의 활동은 집계에 섞이지 않고, 활동이 없는 자녀는 빈 집계를 받는다")
    void keepPreferencesOfDifferentChildrenApart() {
        add(CHILD_1_ID, new PreferenceActivity(PRODUCT_A_CATEGORY_ID, PRODUCT_A_BRAND, true));
        add(OTHER_CHILD_ID, new PreferenceActivity(OTHER_CATEGORY_ID, OTHER_BRAND, false));

        ChildPreference child = childPreferenceRepository.findByChildProfileId(CHILD_1_ID);
        ChildPreference otherChild = childPreferenceRepository.findByChildProfileId(OTHER_CHILD_ID);
        ChildPreference childWithoutActivity = childPreferenceRepository.findByChildProfileId(NO_ACTIVITY_CHILD_ID);

        assertThat(child.categoryPoints(PRODUCT_A_CATEGORY_ID)).isEqualTo(5);
        assertThat(child.brandPoints(PRODUCT_A_BRAND)).isEqualTo(2);
        assertThat(child.categoryPoints(OTHER_CATEGORY_ID)).isZero();
        assertThat(child.brandPoints(OTHER_BRAND)).isZero();
        assertThat(otherChild.categoryPoints(OTHER_CATEGORY_ID)).isEqualTo(3);
        assertThat(otherChild.brandPoints(OTHER_BRAND)).isEqualTo(2);
        assertThat(otherChild.categoryPoints(PRODUCT_A_CATEGORY_ID)).isZero();
        assertThat(otherChild.brandPoints(PRODUCT_A_BRAND)).isZero();
        assertThat(childWithoutActivity.categoryPoints(PRODUCT_A_CATEGORY_ID)).isZero();
        assertThat(childWithoutActivity.brandPoints(PRODUCT_A_BRAND)).isZero();
    }

    @Test
    @DisplayName("같은 자녀 · 카테고리의 집계 줄을 두 줄 저장하면 UNIQUE 제약이 막는다")
    void rejectSecondCategoryRowOfSameChild() {
        categoryPreferences.saveAndFlush(ChildCategoryPreference.of(CHILD_1_ID, PRODUCT_A_CATEGORY_ID, 1, 0));

        assertThatThrownBy(() -> categoryPreferences.saveAndFlush(
            ChildCategoryPreference.of(CHILD_1_ID, PRODUCT_A_CATEGORY_ID, 1, 0)))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(categoryPreferences.findAllByChildProfileId(CHILD_1_ID)).hasSize(1);
    }

    @Test
    @DisplayName("같은 자녀 · 브랜드의 집계 줄을 두 줄 저장하면 UNIQUE 제약이 막는다")
    void rejectSecondBrandRowOfSameChild() {
        brandPreferences.saveAndFlush(ChildBrandPreference.of(CHILD_1_ID, PRODUCT_A_BRAND, 1));

        assertThatThrownBy(() -> brandPreferences.saveAndFlush(
            ChildBrandPreference.of(CHILD_1_ID, PRODUCT_A_BRAND, 1)))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(brandPreferences.findAllByChildProfileId(CHILD_1_ID)).hasSize(1);
    }

    private void add(Long childProfileId, PreferenceActivity activity) {
        transactionTemplate.executeWithoutResult(status -> childPreferenceRepository.add(childProfileId, activity));
    }

    private void subtract(Long childProfileId, PreferenceActivity activity) {
        transactionTemplate.executeWithoutResult(
            status -> childPreferenceRepository.subtract(childProfileId, activity));
    }
}
