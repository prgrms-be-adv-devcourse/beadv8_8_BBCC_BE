package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.CHILD_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_BRAND;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_CATEGORY_ID;
import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 자녀별 선호 집계를 실제 DB에 올리고 내리고 읽는 규칙을 확인한다. 리포지토리는 트랜잭션을 열지 않으므로 쓰기는 TransactionTemplate으로 감싼다.
 */
@IntegrationTest
class ChildPreferenceRepositoryIntegrationTest {

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

    private void add(Long childProfileId, PreferenceActivity activity) {
        transactionTemplate.executeWithoutResult(status -> childPreferenceRepository.add(childProfileId, activity));
    }

    private void subtract(Long childProfileId, PreferenceActivity activity) {
        transactionTemplate.executeWithoutResult(
            status -> childPreferenceRepository.subtract(childProfileId, activity));
    }
}
