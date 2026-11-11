package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.domain.recommended.ChildActivityEvidence;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
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
}
