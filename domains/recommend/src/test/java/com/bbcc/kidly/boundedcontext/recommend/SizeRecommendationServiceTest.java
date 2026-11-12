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
import com.bbcc.kidly.boundedcontext.recommend.domain.growth.WearablePeriod;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.GarmentPart;
import com.bbcc.kidly.boundedcontext.recommend.domain.wearpreview.HemPosition;
import com.bbcc.kidly.boundedcontext.recommend.domain.wearpreview.SleevePosition;
import com.bbcc.kidly.boundedcontext.recommend.domain.wearpreview.WearPreview;
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

    // 기록이 없어 표 값(만 7세 남아 6.00cm/년 ÷ 12)으로 계산한 성장 속도다
    private static final SizeAiQuestion.GrowthSpeed GROWTH = new SizeAiQuestion.GrowthSpeed(new BigDecimal("0.50"),
        null, 0, new BigDecimal("0"), 0, new BigDecimal("0.50"), "한국아동패널");

    private static final SizeAiQuestion QUESTION = new SizeAiQuestion(null, List.of(), null, null, GROWTH, List.of());

    private static final ReferenceFit REFERENCE_FIT = new ReferenceFit(GarmentPart.TOP, GarmentPart.TOP, List.of(
        new ReferenceFit.Item("CHEST_WIDTH", new BigDecimal("34.0"), List.of(
            new ReferenceFit.SizeDiff("120", new BigDecimal("36.0"), new BigDecimal("2.0"))))));

    private static final SizeAiQuestion QUESTION_WITH_FIT =
        new SizeAiQuestion(null, List.of(), null, REFERENCE_FIT, GROWTH, List.of());

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
            PRODUCT_ID, CHILD_ID, sizeFreeProduct, SizeRecommendation.sizeFree(), KEY_INPUT, QUESTION, EVIDENCE, null, null));

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
            PRODUCT_ID, CHILD_ID, RecommendFixture.productA(), rule, KEY_INPUT, QUESTION, EVIDENCE, null, null);
    }

    @Test
    @DisplayName("FIT-09 AI 답을 채택하면 추천 사이즈의 항목별 판단과 기준 핏을 결과에 담는다")
    void resultHasAdoptedJudgementsAndReferenceFit() {
        given(reader.read(MEMBER_ID, PRODUCT_ID, CHILD_ID)).willReturn(draftWithReferenceFit());
        given(aiSizeChooser.choose(QUESTION_WITH_FIT)).willReturn(answerWithChestDiff("2.0"));
        givenAiResponseServiceCallsChooser();

        SizeRecommendationResult result = service().recommend(MEMBER_ID, PRODUCT_ID, CHILD_ID);

        assertThat(result.source()).isEqualTo("AI");
        assertThat(result.itemJudgements()).containsExactly(new SizeRecommendationResult.ItemJudgement(
            "CHEST_WIDTH", new BigDecimal("2.0"), "ROOMY", new BigDecimal("0.0"), new BigDecimal("3.0")));
        assertThat(result.referenceFit().productPart()).isEqualTo("TOP");
        assertThat(result.referenceFit().items()).extracting(SizeRecommendationResult.FitItem::code)
            .containsExactly("CHEST_WIDTH");
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    @DisplayName("FIT-09 · FIT-11 AI 답을 채택하면 프롬프트 판과 항목별 판단을 로그 한 줄로 남기고 자녀 정보는 남기지 않는다")
    void logAdoptedJudgementsWithPromptVersion(CapturedOutput output) {
        given(reader.read(MEMBER_ID, PRODUCT_ID, CHILD_ID)).willReturn(draftWithReferenceFit());
        given(aiSizeChooser.choose(QUESTION_WITH_FIT)).willReturn(answerWithChestDiff("2.0"));
        givenAiResponseServiceCallsChooser();

        service().recommend(MEMBER_ID, PRODUCT_ID, CHILD_ID);

        assertThat(output.getOut()).contains("promptVersion=1", "productId=10", "CHEST_WIDTH", "ROOMY")
            .doesNotContain("childId", "memberId", "=" + CHILD_ID);
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    @DisplayName("FIT-10 · FIT-11 AI 답을 검증에서 버리면 규칙 결과로 답하고 프롬프트 판과 함께 버렸다는 로그를 남긴다")
    void logDiscardedAnswerWithPromptVersion(CapturedOutput output) {
        given(reader.read(MEMBER_ID, PRODUCT_ID, CHILD_ID)).willReturn(draftWithReferenceFit());
        given(aiSizeChooser.choose(QUESTION_WITH_FIT)).willReturn(answerWithChestDiff("2.5"));
        givenAiResponseServiceCallsChooser();

        SizeRecommendationResult result = service().recommend(MEMBER_ID, PRODUCT_ID, CHILD_ID);

        assertThat(result.source()).isEqualTo("RULE");
        assertThat(output.getOut()).contains("규칙 결과로 답합니다", "promptVersion=1", "productId=10")
            .doesNotContain("childId", "memberId");
    }

    @Test
    @DisplayName("GROW-01 · WEAR-03 AI 모델이 없어 규칙 결과로 답해도 성장 속도와 사이즈별 입을 수 있는 기간을 결과에 담는다")
    void resultHasGrowthSpeedAndWearablePeriodsWithoutAi() {
        SizeAiQuestion question = new SizeAiQuestion(null, List.of(), null, null,
            new SizeAiQuestion.GrowthSpeed(new BigDecimal("0.50"), null, 0, new BigDecimal("0"), 0,
                new BigDecimal("0.50"), "한국아동패널"),
            List.of(new WearablePeriod("120", 20, null, 20)));
        given(reader.read(MEMBER_ID, PRODUCT_ID, CHILD_ID)).willReturn(new SizeRecommendationDraft(
            PRODUCT_ID, CHILD_ID, RecommendFixture.productA(), RULE_120, KEY_INPUT, question, EVIDENCE, null, null));

        SizeRecommendationResult result = new SizeRecommendationService(reader, aiResponseService, Optional.empty())
            .recommend(MEMBER_ID, PRODUCT_ID, CHILD_ID);

        assertThat(result.source()).isEqualTo("RULE");
        assertThat(result.growthSpeed()).isEqualTo(new SizeRecommendationResult.GrowthSpeed(
            new BigDecimal("0.50"), null, 0, new BigDecimal("0"), 0, new BigDecimal("0.50"), "한국아동패널"));
        assertThat(result.wearablePeriods())
            .containsExactly(new SizeRecommendationResult.WearablePeriod("120", 20, null, 20));
    }

    @Test
    @DisplayName("PRV-05 AI 모델이 없어 규칙 결과로 답해도 착용 미리보기를 결과에 담는다")
    void resultHasWearPreviewWithoutAi() {
        WearPreview preview = new WearPreview(GarmentPart.TOP,
            new WearPreview.Body(98.6, 92.8, 71.9, 58.8, 53.5, 30.5, 5.3, 39.4),
            List.of(new WearPreview.SizeWear("120", 53.6, HemPosition.HIP, 30.0, SleevePosition.ABOVE_WRIST),
                new WearPreview.SizeWear("130", null, null, null, null)));
        given(reader.read(MEMBER_ID, PRODUCT_ID, CHILD_ID)).willReturn(new SizeRecommendationDraft(
            PRODUCT_ID, CHILD_ID, RecommendFixture.productA(), RULE_120, KEY_INPUT, QUESTION, EVIDENCE, preview, null));

        SizeRecommendationResult result = new SizeRecommendationService(reader, aiResponseService, Optional.empty())
            .recommend(MEMBER_ID, PRODUCT_ID, CHILD_ID);

        assertThat(result.wearPreview()).isEqualTo(new SizeRecommendationResult.WearPreview("TOP",
            new SizeRecommendationResult.Body(98.6, 92.8, 71.9, 58.8, 53.5, 30.5, 5.3, 39.4),
            List.of(new SizeRecommendationResult.SizeWear("120", 53.6, "HIP", 30.0, "ABOVE_WRIST"),
                new SizeRecommendationResult.SizeWear("130", null, null, null, null))));
    }

    @Test
    @DisplayName("PRV-05 착용 미리보기가 없는 상품(사이즈 무관 · 모자 · 양말)이면 결과의 착용 미리보기는 null이다")
    void resultHasNoWearPreviewWhenNotPreviewed() {
        given(reader.read(MEMBER_ID, PRODUCT_ID, CHILD_ID)).willReturn(new SizeRecommendationDraft(
            PRODUCT_ID, CHILD_ID, RecommendFixture.productA(), RULE_120, KEY_INPUT, QUESTION, EVIDENCE, null, null));

        SizeRecommendationResult result = new SizeRecommendationService(reader, aiResponseService, Optional.empty())
            .recommend(MEMBER_ID, PRODUCT_ID, CHILD_ID);

        assertThat(result.wearPreview()).isNull();
    }

    // 상의 기준 옷 가슴단면 34cm와 상품 120의 가슴단면 36cm를 비교한 기준 핏을 넘긴 초안이다
    private static SizeRecommendationDraft draftWithReferenceFit() {
        return new SizeRecommendationDraft(PRODUCT_ID, CHILD_ID, RecommendFixture.productA(), RULE_120, KEY_INPUT,
            QUESTION_WITH_FIT, new SizeAiEvidence(REFERENCE_FIT, Set.of(new BigDecimal("2.0"))), null, null);
    }

    private static AiSizeAnswer answerWithChestDiff(String diffCm) {
        return new AiSizeAnswer("120", "ROOMY",
            List.of(new AiSizeAnswer.Reason("MEASUREMENT", "가슴단면이 2cm 넉넉해요.", null)),
            List.of(new AiSizeAnswer.Judgement("CHEST_WIDTH", new BigDecimal(diffCm), "ROOMY",
                new BigDecimal("0.0"), new BigDecimal("3.0"))));
    }

    // AI 응답 서비스가 저장된 답 없이 넘겨받은 호출을 그대로 실행한 것처럼 한다
    @SuppressWarnings("unchecked")
    private void givenAiResponseServiceCallsChooser() {
        given(aiResponseService.respond(
            eq(AiResponseType.SIZE_RECOMMENDATION), eq(KEY_INPUT), eq(AiSizeAnswer.class), any()))
            .willAnswer(invocation -> Optional.ofNullable(((Supplier<AiSizeAnswer>) invocation.getArgument(3)).get()));
    }
}
