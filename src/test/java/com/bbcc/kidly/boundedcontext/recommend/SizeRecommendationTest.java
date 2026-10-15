package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.recommend.domain.size.Fit;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.SizeNotice;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.SizeRecommendation;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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

    @Test
    @DisplayName("INV-R04 추천 사이즈가 후보 사이즈에 없으면 추천 결과를 만들지 않는다")
    void rejectRecommendedSizeOutsideCandidates() {
        assertThatThrownBy(() ->
            SizeRecommendation.recommended("130", Fit.PROPER, List.of(), List.of("110", "120")))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
