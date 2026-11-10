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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

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

    @ParameterizedTest(name = "AI 사이즈 [{0}]")
    @NullAndEmptySource
    @ValueSource(strings = {"130", " "})
    @DisplayName("AI가 상품에 없는 사이즈를 고르거나 사이즈를 비우면 규칙 결과를 그대로 쓴다")
    void keepRuleWhenAiSizeIsNotInProduct(String aiSize) {
        AiSizeAnswer answer = new AiSizeAnswer(aiSize, "TIGHT",
            List.of(new AiSizeAnswer.Reason("HEIGHT", HEIGHT_DETAIL, null)));

        SizeRecommendation result = RULE_120.decideWith(RecommendFixture.productA(), answer);

        assertRuleResult(result);
    }

    private static void assertRuleResult(SizeRecommendation result) {
        assertThat(result.getRecommendedSize()).isEqualTo("120");
        assertThat(result.getFit()).isEqualTo(Fit.PROPER);
        assertThat(result.getSource()).isEqualTo(SizeRecommendationSource.RULE);
        assertThat(result.getCandidateSizes()).containsExactly("110", "120");
        assertThat(result.getNotices()).containsExactly(SizeNotice.BODY_TYPE_ADJUSTED);
        assertThat(result.getReasons()).isEmpty();
    }
}
