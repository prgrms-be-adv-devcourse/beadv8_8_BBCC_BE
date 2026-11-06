package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.CHILD_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_BRAND;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_CATEGORY_ID;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.domain.preference.ChildPreference;
import com.bbcc.kidly.boundedcontext.recommend.domain.preference.ChildPreferenceRepository;
import com.bbcc.kidly.boundedcontext.recommend.domain.preference.PreferenceActivity;
import com.bbcc.kidly.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
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

    private void add(Long childProfileId, PreferenceActivity activity) {
        transactionTemplate.executeWithoutResult(status -> childPreferenceRepository.add(childProfileId, activity));
    }
}
