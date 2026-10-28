package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.recommend.domain.size.Fit;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.SizeNotice;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.SizeRecommendation;
import java.util.List;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class SizeRecommendationTest {

    @Test
    @DisplayName("맞는 사이즈가 없으면 사이즈와 적합도는 없고 안내는 NO_MATCHING_SIZE 하나다")
    void noMatchingSize() {
        SizeRecommendation result = SizeRecommendation.noMatchingSize();

        assertThat(result.getRecommendedSize()).isNull();
        assertThat(result.getFit()).isNull();
        assertThat(result.getNotices()).containsExactly(SizeNotice.NO_MATCHING_SIZE);
    }

    @Test
    @DisplayName("사이즈 무관 상품이면 사이즈와 적합도는 없고 안내는 SIZE_FREE 하나다")
    void sizeFree() {
        SizeRecommendation result = SizeRecommendation.sizeFree();

        assertThat(result.getRecommendedSize()).isNull();
        assertThat(result.getFit()).isNull();
        assertThat(result.getNotices()).containsExactly(SizeNotice.SIZE_FREE);
    }

    @Test
    @DisplayName("INV-R04 맞는 사이즈가 없거나 사이즈 무관 상품이면 후보 사이즈는 빈 목록이다")
    void noCandidatesWithoutRecommendedSize() {
        assertThat(SizeRecommendation.noMatchingSize().getCandidateSizes()).isEmpty();
        assertThat(SizeRecommendation.sizeFree().getCandidateSizes()).isEmpty();
    }

    @Test
    @DisplayName("INV-R04 추천 결과는 받은 후보 사이즈를 순서대로 가진다")
    void keepCandidateSizes() {
        SizeRecommendation result =
            SizeRecommendation.recommended("120", Fit.ROOMY, List.of(), List.of("110", "120"));

        assertThat(result.getCandidateSizes()).containsExactly("110", "120");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("recommendationsWithNull")
    @DisplayName("INV-R04 추천 결과의 사이즈 · 적합도 · 안내 목록 · 후보 목록이 null이면 만들지 않는다")
    void rejectRecommendationWithNull(ThrowingCallable recommended) {
        assertThatThrownBy(recommended)
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("필수입니다");
    }

    static Stream<Named<ThrowingCallable>> recommendationsWithNull() {
        List<String> candidates = List.of("110", "120");
        return Stream.of(
            Named.of("사이즈", () -> SizeRecommendation.recommended(null, Fit.ROOMY, List.of(), candidates)),
            Named.of("적합도", () -> SizeRecommendation.recommended("120", null, List.of(), candidates)),
            Named.of("안내 목록", () -> SizeRecommendation.recommended("120", Fit.ROOMY, null, candidates)),
            Named.of("후보 목록", () -> SizeRecommendation.recommended("120", Fit.ROOMY, List.of(), null)));
    }

    @Test
    @DisplayName("INV-R04 추천 사이즈가 후보 사이즈에 없으면 추천 결과를 만들지 않는다")
    void rejectRecommendedSizeOutsideCandidates() {
        assertThatThrownBy(() ->
            SizeRecommendation.recommended("130", Fit.PROPER, List.of(), List.of("110", "120")))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
