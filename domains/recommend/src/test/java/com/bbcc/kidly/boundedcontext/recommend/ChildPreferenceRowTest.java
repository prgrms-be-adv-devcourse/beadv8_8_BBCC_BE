package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.bbcc.kidly.boundedcontext.recommend.domain.preference.ChildBrandPreference;
import com.bbcc.kidly.boundedcontext.recommend.domain.preference.ChildCategoryPreference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 집계 줄을 만들 때 잘못된 값을 거절하는지 확인한다.
 */
class ChildPreferenceRowTest {

    private static final Long CHILD_ID = 11L;

    private static final Long CATEGORY_ID = 52L;

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   "})
    @DisplayName("PREF-04 빈 문자열이나 공백만 있는 브랜드로는 브랜드 줄을 만들 수 없다")
    void rejectBlankBrand(String brand) {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> ChildBrandPreference.of(CHILD_ID, brand, 1))
            .withMessage("브랜드는 비어 있을 수 없습니다");
    }

    @Test
    @DisplayName("PREF-04 자녀 ID나 브랜드가 null이면 브랜드 줄을 만들 수 없다")
    void rejectNullBrandRowValues() {
        assertThatNullPointerException().isThrownBy(() -> ChildBrandPreference.of(null, "키들리", 1));
        assertThatNullPointerException().isThrownBy(() -> ChildBrandPreference.of(CHILD_ID, null, 1));
    }

    @Test
    @DisplayName("PREF-02 · 03 자녀 ID나 카테고리 ID가 null이면 카테고리 줄을 만들 수 없다")
    void rejectNullCategoryRowValues() {
        assertThatNullPointerException().isThrownBy(() -> ChildCategoryPreference.of(null, CATEGORY_ID, 1, 0));
        assertThatNullPointerException().isThrownBy(() -> ChildCategoryPreference.of(CHILD_ID, null, 1, 0));
    }
}
