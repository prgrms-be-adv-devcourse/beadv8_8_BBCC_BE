package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.domain.size.AiSizeAnswer;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.Fit;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.ProductSizes;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.SizeRange;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.SizeNotice;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.SizeReason;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.SizeReasonType;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.SizeRecommendation;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.SizeRecommendationSource;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
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

    @ParameterizedTest(name = "{0}")
    @MethodSource("unusableAnswers")
    @DisplayName("AI 답이 없거나 적합도가 모르는 값이면 규칙 결과를 그대로 쓴다")
    void keepRuleWhenAiAnswerIsUnusable(AiSizeAnswer answer) {
        SizeRecommendation result = RULE_120.decideWith(RecommendFixture.productA(), answer);

        assertRuleResult(result);
    }

    static Stream<Named<AiSizeAnswer>> unusableAnswers() {
        List<AiSizeAnswer.Reason> reasons = List.of(new AiSizeAnswer.Reason("HEIGHT", HEIGHT_DETAIL, null));
        return Stream.of(
            Named.of("AI 답 없음", null),
            Named.of("적합도 없음", new AiSizeAnswer("110", null, reasons)),
            Named.of("적합도 빈 값", new AiSizeAnswer("110", "", reasons)),
            Named.of("모르는 적합도", new AiSizeAnswer("110", "LOOSE", reasons)),
            Named.of("소문자 적합도", new AiSizeAnswer("110", "tight", reasons)));
    }

    @Test
    @DisplayName("사이즈 무관 상품이면 AI가 그 상품의 사이즈 이름을 골라도 규칙 결과를 그대로 쓴다")
    void keepRuleForSizeFreeProduct() {
        ProductSizes sizeFreeProduct = ProductSizes.of(true, List.of(SizeRange.of("FREE", 90, 130)));
        AiSizeAnswer answer = new AiSizeAnswer("FREE", "PROPER",
            List.of(new AiSizeAnswer.Reason("HEIGHT", HEIGHT_DETAIL, null)));

        SizeRecommendation result = SizeRecommendation.sizeFree().decideWith(sizeFreeProduct, answer);

        assertThat(result.getRecommendedSize()).isNull();
        assertThat(result.getFit()).isNull();
        assertThat(result.getSource()).isEqualTo(SizeRecommendationSource.RULE);
        assertThat(result.getNotices()).containsExactly(SizeNotice.SIZE_FREE);
        assertThat(result.getReasons()).isEmpty();
    }

    @Test
    @DisplayName("규칙이 맞는 사이즈를 찾지 못해도 AI가 상품에 있는 사이즈를 고르면 그 답을 쓴다")
    void adoptAiAnswerWhenRuleFoundNoSize() {
        AiSizeAnswer answer = new AiSizeAnswer("120", "ROOMY",
            List.of(new AiSizeAnswer.Reason("HEIGHT", HEIGHT_DETAIL, null)));

        SizeRecommendation result = SizeRecommendation.noMatchingSize().decideWith(RecommendFixture.productA(), answer);

        assertThat(result.getRecommendedSize()).isEqualTo("120");
        assertThat(result.getFit()).isEqualTo(Fit.ROOMY);
        assertThat(result.getSource()).isEqualTo(SizeRecommendationSource.AI);
        assertThat(result.getCandidateSizes()).isEmpty();
        assertThat(result.getNotices()).isEmpty();
    }

    @Test
    @DisplayName("INV-R04 AI가 규칙 후보 밖의 상품 사이즈를 골라도 그 답을 쓰고, 후보는 규칙 후보 그대로 둔다")
    void adoptAiAnswerOutsideRuleCandidates() {
        SizeRecommendation ruleOnly120 = SizeRecommendation.recommended("120", Fit.PROPER, List.of(), List.of("120"));
        AiSizeAnswer answer = new AiSizeAnswer("110", "TIGHT",
            List.of(new AiSizeAnswer.Reason("HEIGHT", HEIGHT_DETAIL, null)));

        SizeRecommendation result = ruleOnly120.decideWith(RecommendFixture.productA(), answer);

        assertThat(result.getRecommendedSize()).isEqualTo("110");
        assertThat(result.getSource()).isEqualTo(SizeRecommendationSource.AI);
        assertThat(result.getCandidateSizes()).containsExactly("120");
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
