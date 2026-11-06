package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.domain.preference.ChildBrandPreference;
import com.bbcc.kidly.boundedcontext.recommend.domain.preference.ChildCategoryPreference;
import com.bbcc.kidly.boundedcontext.recommend.domain.preference.ChildPreference;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 저장된 집계 줄로 만든 선호 집계가 활동을 하나씩 더해 만든 집계와 같은 점수를 내는지 확인한다.
 */
class ChildPreferenceOfTest {

    private static final Long CHILD_ID = 11L;

    private static final Long CATEGORY_ID = 52L;

    private static final String BRAND = "키들리";

    @ParameterizedTest(name = "활동 {0}번, 담기 {1}번 → {2}점")
    @CsvSource({
        "1, 0, 3",
        "2, 0, 3",
        "1, 1, 5",
        "2, 1, 5",
        "0, 0, 0",
        "-1, 0, 0",
    })
    @DisplayName("PREF-02 · 03 저장된 카테고리 줄은 활동 횟수가 1 이상이면 3점, 담기 횟수도 1 이상이면 5점이고 0 이하는 나오지 않은 것이다")
    void categoryPointsOfStoredRow(int activityCount, int cartAddCount, int expected) {
        ChildPreference preference = ChildPreference.of(
            List.of(ChildCategoryPreference.of(CHILD_ID, CATEGORY_ID, activityCount, cartAddCount)),
            List.of());

        assertThat(preference.categoryPoints(CATEGORY_ID)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "활동 {0}번 → {1}점")
    @CsvSource({
        "1, 2",
        "2, 2",
        "0, 0",
        "-1, 0",
    })
    @DisplayName("PREF-04 저장된 브랜드 줄은 활동 횟수가 1 이상이면 2점이고 0 이하는 나오지 않은 것이다")
    void brandPointsOfStoredRow(int activityCount, int expected) {
        ChildPreference preference = ChildPreference.of(
            List.of(),
            List.of(ChildBrandPreference.of(CHILD_ID, BRAND, activityCount)));

        assertThat(preference.brandPoints(BRAND)).isEqualTo(expected);
    }
}
