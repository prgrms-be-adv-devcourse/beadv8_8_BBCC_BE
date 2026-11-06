package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.domain.preference.ChildPreference;
import com.bbcc.kidly.boundedcontext.recommend.domain.preference.PreferenceActivity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ChildPreferenceTest {

    private static final Long CATEGORY_ID = 52L;

    private static final String BRAND = "키들리";

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
}
