package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.domain.size.AiSizeAnswer;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.Fit;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.SizeNotice;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.SizeReason;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.SizeReasonType;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.SizeRecommendation;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.SizeRecommendationSource;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SizeRecommendationAiTest {

    private static final SizeRecommendation RULE_120 = SizeRecommendation.recommended(
        "120", Fit.PROPER, List.of(SizeNotice.BODY_TYPE_ADJUSTED), List.of("110", "120"));

    private static final String HEIGHT_DETAIL = "키 114cm는 110의 권장 키 안이에요.";

    @Test
    @DisplayName("AI가 상품에 있는 사이즈를 고르면 그 사이즈와 적합도로 답하고, 후보는 규칙 그대로, 안내는 비운다")
    void adoptAiAnswerWithinProductSizes() {
        AiSizeAnswer answer = new AiSizeAnswer("110", "TIGHT",
            List.of(new AiSizeAnswer.Reason("HEIGHT", HEIGHT_DETAIL, null)));

        SizeRecommendation result = RULE_120.decideWith(RecommendFixture.productA(), answer);

        assertThat(result.getRecommendedSize()).isEqualTo("110");
        assertThat(result.getFit()).isEqualTo(Fit.TIGHT);
        assertThat(result.getSource()).isEqualTo(SizeRecommendationSource.AI);
        assertThat(result.getCandidateSizes()).containsExactly("110", "120");
        assertThat(result.getNotices()).isEmpty();
        assertThat(result.getReasons())
            .containsExactly(new SizeReason(SizeReasonType.HEIGHT, HEIGHT_DETAIL, null));
    }
}
