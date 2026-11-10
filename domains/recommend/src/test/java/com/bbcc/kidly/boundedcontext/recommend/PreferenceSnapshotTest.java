package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.EmbeddingVectorFixture.axis;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.bbcc.kidly.boundedcontext.recommend.domain.preference.BrandAffinity;
import com.bbcc.kidly.boundedcontext.recommend.domain.preference.CategoryAffinity;
import com.bbcc.kidly.boundedcontext.recommend.domain.preference.PreferenceSnapshot;
import com.bbcc.kidly.boundedcontext.recommend.domain.preference.TasteVector;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

class PreferenceSnapshotTest {

    private static final LocalDateTime CHILD_CALCULATED_AT = LocalDateTime.of(2026, 11, 10, 3, 0);

    private static final LocalDateTime MEMBER_CALCULATED_AT = LocalDateTime.of(2026, 11, 10, 3, 5);

    private static final Long CATEGORY_ID = 52L;

    private static final String BRAND = "키들리";

    @ParameterizedTest(name = "자녀 {0} · 회원 {1} → {2}")
    @CsvSource({
        "0.6, 0.3, 0.66",
        "0.6, , 0.6",
        ", 0.3, 0.15",
    })
    @DisplayName("PREF-12 친밀도는 1 − (1 − 자녀)(1 − 0.5 × 회원)이고, 한쪽에만 있는 카테고리 · 브랜드도 남는다")
    void combineAffinities(Double childScore, Double memberScore, double expected) {
        PreferenceSnapshot child = affinitySnapshot(childScore, CHILD_CALCULATED_AT);
        PreferenceSnapshot member = affinitySnapshot(memberScore, MEMBER_CALCULATED_AT);

        PreferenceSnapshot combined = PreferenceSnapshot.combine(child, member);

        assertThat(combined.categoryAffinity(CATEGORY_ID)).isCloseTo(expected, within(1e-9));
        assertThat(combined.brandAffinity(BRAND)).isCloseTo(expected, within(1e-9));
    }

    @Test
    @DisplayName("PREF-12 취향 벡터는 정규화(자녀 무게 합 × 자녀 벡터 + 0.5 × 회원 무게 합 × 회원 벡터)다")
    void combineTasteVectors() {
        PreferenceSnapshot child = tasteSnapshot(new TasteVector(axis(0), 0.6, 1), CHILD_CALCULATED_AT);
        PreferenceSnapshot member = tasteSnapshot(new TasteVector(axis(1), 0.6, 2), MEMBER_CALCULATED_AT);

        TasteVector combined = PreferenceSnapshot.combine(child, member).tasteVector();

        // 0.6 × 축0 + 0.5 × 0.6 × 축1을 길이 1로 맞추면 (2, 1) / √5다
        float[] values = combined.embedding().values();
        assertThat((double) values[0]).isCloseTo(2 / Math.sqrt(5), within(1e-6));
        assertThat((double) values[1]).isCloseTo(1 / Math.sqrt(5), within(1e-6));
        assertThat(combined.weightSum()).isCloseTo(0.9, within(1e-9));
        assertThat(combined.activityCount()).isEqualTo(3);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("oneSidedTasteVectors")
    @DisplayName("PREF-12 취향 벡터가 한쪽에만 있으면 그 벡터를 그대로 쓴다")
    void oneSidedTasteVectorIsKept(String description, PreferenceSnapshot child, PreferenceSnapshot member,
        int expectedAxis, double expectedWeightSum) {
        TasteVector combined = PreferenceSnapshot.combine(child, member).tasteVector();

        assertThat(combined.embedding()).isEqualTo(axis(expectedAxis));
        assertThat(combined.weightSum()).isCloseTo(expectedWeightSum, within(1e-9));
        assertThat(combined.activityCount()).isEqualTo(2);
    }

    static Stream<Arguments> oneSidedTasteVectors() {
        return Stream.of(
            Arguments.of("자녀만",
                tasteSnapshot(new TasteVector(axis(0), 0.8, 2), CHILD_CALCULATED_AT),
                PreferenceSnapshot.empty(), 0, 0.8),
            Arguments.of("회원만",
                PreferenceSnapshot.empty(),
                tasteSnapshot(new TasteVector(axis(1), 0.8, 2), MEMBER_CALCULATED_AT), 1, 0.4));
    }

    @Test
    @DisplayName("PREF-12 둘 다 취향 벡터가 없으면 합친 결과에도 없다")
    void noTasteVectorWhenBothMissing() {
        PreferenceSnapshot child = affinitySnapshot(0.6, CHILD_CALCULATED_AT);
        PreferenceSnapshot member = affinitySnapshot(0.3, MEMBER_CALCULATED_AT);

        assertThat(PreferenceSnapshot.combine(child, member).tasteVector()).isNull();
    }

    @ParameterizedTest(name = "자녀 결과 있음 {0}")
    @CsvSource({
        "true, 2026-11-10T03:00:00",
        "false, 2026-11-10T03:05:00",
    })
    @DisplayName("PREF-12 합친 결과의 계산 시각은 자녀 것이고, 자녀 결과가 없으면 회원 것이다")
    void calculatedAtPrefersChild(boolean childCalculated, LocalDateTime expected) {
        PreferenceSnapshot child =
            childCalculated ? affinitySnapshot(0.6, CHILD_CALCULATED_AT) : PreferenceSnapshot.empty();
        PreferenceSnapshot member = affinitySnapshot(0.3, MEMBER_CALCULATED_AT);

        assertThat(PreferenceSnapshot.combine(child, member).calculatedAt()).isEqualTo(expected);
    }

    @Test
    @DisplayName("합친 결과의 카테고리 · 브랜드 목록은 바꿀 수 없다")
    void combinedAffinitiesAreUnmodifiable() {
        PreferenceSnapshot combined = PreferenceSnapshot.combine(
            affinitySnapshot(0.6, CHILD_CALCULATED_AT), affinitySnapshot(0.3, MEMBER_CALCULATED_AT));

        assertThatThrownBy(() -> combined.categories().add(new CategoryAffinity(1L, 0.1)))
            .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> combined.brands().add(new BrandAffinity("다른", 0.1)))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    private static PreferenceSnapshot affinitySnapshot(Double score, LocalDateTime calculatedAt) {
        if (score == null) {
            return new PreferenceSnapshot(List.of(), List.of(), null, calculatedAt);
        }
        return new PreferenceSnapshot(
            List.of(new CategoryAffinity(CATEGORY_ID, score)), List.of(new BrandAffinity(BRAND, score)),
            null, calculatedAt);
    }

    private static PreferenceSnapshot tasteSnapshot(TasteVector tasteVector, LocalDateTime calculatedAt) {
        return new PreferenceSnapshot(List.of(), List.of(), tasteVector, calculatedAt);
    }
}
