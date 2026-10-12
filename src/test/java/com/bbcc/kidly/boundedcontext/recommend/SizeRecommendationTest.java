package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.domain.SizeNotice;
import com.bbcc.kidly.boundedcontext.recommend.domain.SizeRecommendation;
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
}
