package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import com.bbcc.kidly.boundedcontext.recommend.app.ai.AiResponseService;
import com.bbcc.kidly.boundedcontext.recommend.app.port.size.AiSizeChooser;
import com.bbcc.kidly.boundedcontext.recommend.app.port.size.SizeAiQuestion;
import com.bbcc.kidly.boundedcontext.recommend.app.size.SizeRecommendationService;
import com.bbcc.kidly.boundedcontext.recommend.app.size.dto.SizeRecommendationResult;
import com.bbcc.kidly.boundedcontext.recommend.app.size.support.SizeRecommendationDraft;
import com.bbcc.kidly.boundedcontext.recommend.app.size.support.SizeRecommendationReader;
import com.bbcc.kidly.boundedcontext.recommend.domain.ai.AiResponseKey;
import com.bbcc.kidly.boundedcontext.recommend.domain.ai.AiResponseType;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.GarmentPart;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.ReferenceFit;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.AiSizeAnswer;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.Fit;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.ProductSizes;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.SizeAiEvidence;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.SizeNotice;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.SizeRange;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.SizeRecommendation;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(MockitoExtension.class)
class SizeRecommendationServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long PRODUCT_ID = 10L;
    private static final Long CHILD_ID = 100L;

    private static final SizeRecommendation RULE_120 = SizeRecommendation.recommended(
        "120", Fit.ROOMY, List.of(SizeNotice.BODY_TYPE_ADJUSTED), List.of("110", "120"));

    private static final AiResponseKey.Input KEY_INPUT = new AiResponseKey.Input(
        PRODUCT_ID, CHILD_ID, 82, null, null, null, null, null, null, 1);

    private static final SizeAiQuestion QUESTION = new SizeAiQuestion(null, List.of(), null, null, null);

    // 기준 핏 없이 키 114cm와 사이즈 110을 AI에게 준 질문의 검증 값이다
    private static final SizeAiEvidence EVIDENCE =
        new SizeAiEvidence(null, Set.of(new BigDecimal("114"), new BigDecimal("110")));

    @Mock
    private SizeRecommendationReader reader;

    @Mock
    private AiResponseService aiResponseService;

    @Mock
    private AiSizeChooser aiSizeChooser;

    @Test
    @DisplayName("AI가 상품에 있는 사이즈를 고르면 그 사이즈와 적합도 · 근거로 답하고, 출처는 AI다")
    void answerWithAiChoice() {
        given(reader.read(MEMBER_ID, PRODUCT_ID, CHILD_ID)).willReturn(draftOf(RULE_120));
        AiSizeAnswer answer = new AiSizeAnswer("110", "TIGHT",
            List.of(new AiSizeAnswer.Reason("HEIGHT", "키 114cm는 110의 권장 키 안이에요.", null)), List.of());
        given(aiSizeChooser.choose(QUESTION)).willReturn(answer);
        givenAiResponseServiceCallsChooser();

        SizeRecommendationResult result = service().recommend(MEMBER_ID, PRODUCT_ID, CHILD_ID);

        assertThat(result.productId()).isEqualTo(PRODUCT_ID);
        assertThat(result.childId()).isEqualTo(CHILD_ID);
        assertThat(result.recommendedSize()).isEqualTo("110");
        assertThat(result.fit()).isEqualTo("TIGHT");
        assertThat(result.notices()).isEmpty();
        assertThat(result.candidateSizes()).containsExactly("110", "120");
        assertThat(result.source()).isEqualTo("AI");
        assertThat(result.reasons()).containsExactly(
            new SizeRecommendationResult.Reason("HEIGHT", "키 114cm는 110의 권장 키 안이에요.", null));
    }

    @Test
    @DisplayName("AI 답이 없으면(실패 · 시간 초과 · 호출 상한) 규칙 결과로 답하고, 출처는 RULE이고 근거는 빈 목록이다")
    void answerWithRuleWhenAiGivesNothing() {
        given(reader.read(MEMBER_ID, PRODUCT_ID, CHILD_ID)).willReturn(draftOf(RULE_120));
        given(aiResponseService.respond(
            eq(AiResponseType.SIZE_RECOMMENDATION), eq(KEY_INPUT), eq(AiSizeAnswer.class), any()))
            .willReturn(Optional.empty());

        SizeRecommendationResult result = service().recommend(MEMBER_ID, PRODUCT_ID, CHILD_ID);

        assertRuleResult(result);
    }

    @Test
    @DisplayName("AI 모델이 없으면 AI를 부르지 않고 규칙 결과로 답한다")
    void answerWithRuleWithoutAiModel() {
        given(reader.read(MEMBER_ID, PRODUCT_ID, CHILD_ID)).willReturn(draftOf(RULE_120));

        SizeRecommendationResult result = new SizeRecommendationService(reader, aiResponseService, Optional.empty())
            .recommend(MEMBER_ID, PRODUCT_ID, CHILD_ID);

        assertRuleResult(result);
        verifyNoInteractions(aiResponseService);
    }

    @Test
    @DisplayName("사이즈 구분이 없는 상품이면 AI를 부르지 않고 SIZE_FREE 안내로 답한다")
    void answerSizeFreeWithoutAi() {
        ProductSizes sizeFreeProduct = ProductSizes.of(true, List.of(SizeRange.of("FREE", 90, 130)));
        given(reader.read(MEMBER_ID, PRODUCT_ID, CHILD_ID)).willReturn(new SizeRecommendationDraft(
            PRODUCT_ID, CHILD_ID, sizeFreeProduct, SizeRecommendation.sizeFree(), KEY_INPUT, QUESTION, EVIDENCE));

        SizeRecommendationResult result = service().recommend(MEMBER_ID, PRODUCT_ID, CHILD_ID);

        assertThat(result.recommendedSize()).isNull();
        assertThat(result.source()).isEqualTo("RULE");
        assertThat(result.notices()).extracting(SizeRecommendationResult.Notice::code).containsExactly("SIZE_FREE");
        verifyNoInteractions(aiResponseService, aiSizeChooser);
    }

    private static void assertRuleResult(SizeRecommendationResult result) {
        assertThat(result.recommendedSize()).isEqualTo("120");
        assertThat(result.fit()).isEqualTo("ROOMY");
        assertThat(result.notices()).extracting(SizeRecommendationResult.Notice::code)
            .containsExactly("BODY_TYPE_ADJUSTED");
        assertThat(result.candidateSizes()).containsExactly("110", "120");
        assertThat(result.source()).isEqualTo("RULE");
        assertThat(result.reasons()).isEmpty();
    }

    private SizeRecommendationService service() {
        return new SizeRecommendationService(reader, aiResponseService, Optional.of(aiSizeChooser));
    }

    private static SizeRecommendationDraft draftOf(SizeRecommendation rule) {
        return new SizeRecommendationDraft(
            PRODUCT_ID, CHILD_ID, RecommendFixture.productA(), rule, KEY_INPUT, QUESTION, EVIDENCE);
    }

    // AI 응답 서비스가 저장된 답 없이 넘겨받은 호출을 그대로 실행한 것처럼 한다
    @SuppressWarnings("unchecked")
    @Test
    @ExtendWith(OutputCaptureExtension.class)
    @DisplayName("FIT-09 · FIT-11 AI 답을 채택하면 프롬프트 판과 항목별 판단을 로그 한 줄로 남기고 자녀 정보는 남기지 않는다")
    void logAdoptedJudgementsWithPromptVersion(CapturedOutput output) {
        ReferenceFit referenceFit = new ReferenceFit(GarmentPart.TOP, GarmentPart.TOP, List.of(
            new ReferenceFit.Item("CHEST_WIDTH", new BigDecimal("34.0"), List.of(
                new ReferenceFit.SizeDiff("120", new BigDecimal("36.0"), new BigDecimal("2.0"))))));
        given(reader.read(MEMBER_ID, PRODUCT_ID, CHILD_ID)).willReturn(new SizeRecommendationDraft(
            PRODUCT_ID, CHILD_ID, RecommendFixture.productA(), RULE_120, KEY_INPUT, QUESTION,
            new SizeAiEvidence(referenceFit, Set.of(new BigDecimal("2.0")))));
        given(aiSizeChooser.choose(QUESTION)).willReturn(new AiSizeAnswer("120", "ROOMY",
            List.of(new AiSizeAnswer.Reason("MEASUREMENT", "가슴단면이 2cm 넉넉해요.", null)),
            List.of(new AiSizeAnswer.Judgement("CHEST_WIDTH", new BigDecimal("2.0"), "ROOMY",
                new BigDecimal("0.0"), new BigDecimal("3.0")))));
        givenAiResponseServiceCallsChooser();

        service().recommend(MEMBER_ID, PRODUCT_ID, CHILD_ID);

        assertThat(output.getOut()).contains("promptVersion=1", "productId=10", "CHEST_WIDTH", "ROOMY")
            .doesNotContain("childId", "memberId");
    }

    private void givenAiResponseServiceCallsChooser() {
        given(aiResponseService.respond(
            eq(AiResponseType.SIZE_RECOMMENDATION), eq(KEY_INPUT), eq(AiSizeAnswer.class), any()))
            .willAnswer(invocation -> Optional.ofNullable(((Supplier<AiSizeAnswer>) invocation.getArgument(3)).get()));
    }
}
