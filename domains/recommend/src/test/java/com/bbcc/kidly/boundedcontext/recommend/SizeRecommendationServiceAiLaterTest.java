package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.bbcc.kidly.boundedcontext.recommend.app.ai.RecommendAiMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import com.bbcc.kidly.boundedcontext.recommend.app.ai.AiResponseService;
import com.bbcc.kidly.boundedcontext.recommend.app.port.size.AiSizeChooser;
import com.bbcc.kidly.boundedcontext.recommend.app.port.size.SizeAiQuestion;
import com.bbcc.kidly.boundedcontext.recommend.app.size.SizeRecommendationService;
import com.bbcc.kidly.boundedcontext.recommend.app.size.dto.SizeRecommendationResult;
import com.bbcc.kidly.boundedcontext.recommend.app.size.dto.SizeRecommendationWithAi;
import com.bbcc.kidly.boundedcontext.recommend.app.size.support.SizeRecommendationDraft;
import com.bbcc.kidly.boundedcontext.recommend.app.size.support.SizeRecommendationReader;
import com.bbcc.kidly.boundedcontext.recommend.domain.ai.AiResponseKey;
import com.bbcc.kidly.boundedcontext.recommend.domain.ai.AiResponseType;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.AiSizeAnswer;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.Fit;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.SizeAiEvidence;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.SizeNotice;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.SizeRecommendation;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 사이즈 추천이 AI 답을 기다리지 않고 규칙 결과를 바로 주고, AI 결과는 나중에 주는 동작을 확인한다(SSE 응답이 쓴다).
 */
@ExtendWith(MockitoExtension.class)
class SizeRecommendationServiceAiLaterTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long PRODUCT_ID = 10L;
    private static final Long CHILD_ID = 100L;

    private static final SizeRecommendation RULE_120 = SizeRecommendation.recommended(
        "120", Fit.ROOMY, List.of(SizeNotice.BODY_TYPE_ADJUSTED), List.of("110", "120"));

    private static final AiResponseKey.Input KEY_INPUT = new AiResponseKey.Input(
        PRODUCT_ID, CHILD_ID, 82, null, null, null, null, null, null, 1);

    private static final SizeAiQuestion.GrowthSpeed GROWTH = new SizeAiQuestion.GrowthSpeed(new BigDecimal("0.50"),
        null, 0, new BigDecimal("0"), 0, new BigDecimal("0.50"), "한국아동패널");

    private static final SizeAiQuestion QUESTION =
        new SizeAiQuestion(null, List.of(), null, null, GROWTH, List.of(), List.of(), null);

    // 기준 핏 없이 키 114cm와 사이즈 110을 AI에게 준 질문의 검증 값이다
    private static final SizeAiEvidence EVIDENCE =
        new SizeAiEvidence(null, Set.of(new BigDecimal("114"), new BigDecimal("110")));

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    private final RecommendAiMetrics metrics = new RecommendAiMetrics(meterRegistry, ObservationRegistry.NOOP);

    @Mock
    private SizeRecommendationReader reader;

    @Mock
    private AiResponseService aiResponseService;

    @Mock
    private AiSizeChooser aiSizeChooser;

    @Test
    @DisplayName("기다리지 않는 사이즈 추천은 규칙 결과를 바로 담고, AI 답이 오면 AI가 고른 사이즈와 근거로 끝난다")
    void returnRuleNowAndAiLater() {
        given(reader.read(MEMBER_ID, PRODUCT_ID, CHILD_ID)).willReturn(draft());
        AiSizeAnswer answer = new AiSizeAnswer("110", "TIGHT",
            List.of(new AiSizeAnswer.Reason("HEIGHT", "키 114cm는 110의 권장 키 안이에요.", null)), List.of());
        given(aiResponseService.respondAsync(
            eq(AiResponseType.SIZE_RECOMMENDATION), eq(KEY_INPUT), eq(AiSizeAnswer.class), any()))
            .willReturn(CompletableFuture.completedFuture(Optional.of(answer)));

        SizeRecommendationWithAi result = service().recommendWithAiLater(MEMBER_ID, PRODUCT_ID, CHILD_ID);

        assertThat(result.rule().source()).isEqualTo("RULE");
        assertThat(result.rule().recommendedSize()).isEqualTo("120");
        assertThat(result.rule().reasons()).isEmpty();
        SizeRecommendationResult ai = result.ai().join().orElseThrow();
        assertThat(ai.source()).isEqualTo("AI");
        assertThat(ai.recommendedSize()).isEqualTo("110");
        assertThat(ai.reasons()).containsExactly(
            new SizeRecommendationResult.Reason("HEIGHT", "키 114cm는 110의 권장 키 안이에요.", null));
    }

    @Test
    @DisplayName("기다리지 않는 사이즈 추천에서 AI가 상품에 없는 사이즈를 고르면 검증에서 버려 AI 결과는 빈 값으로 끝난다")
    void completeAiEmptyWhenAnswerRejected() {
        given(reader.read(MEMBER_ID, PRODUCT_ID, CHILD_ID)).willReturn(draft());
        AiSizeAnswer answer = new AiSizeAnswer("150", "PROPER", List.of(), List.of());
        given(aiResponseService.respondAsync(
            eq(AiResponseType.SIZE_RECOMMENDATION), eq(KEY_INPUT), eq(AiSizeAnswer.class), any()))
            .willReturn(CompletableFuture.completedFuture(Optional.of(answer)));

        SizeRecommendationWithAi result = service().recommendWithAiLater(MEMBER_ID, PRODUCT_ID, CHILD_ID);

        assertThat(result.rule().recommendedSize()).isEqualTo("120");
        assertThat(result.ai().join()).isEmpty();
        assertThat(meterRegistry.get("kidly.recommend.ai.rejected").tag("feature", "size_recommendation")
            .counter().count()).isEqualTo(1);
    }

    private SizeRecommendationService service() {
        return new SizeRecommendationService(reader, aiResponseService, Optional.of(aiSizeChooser), metrics);
    }

    private static SizeRecommendationDraft draft() {
        return new SizeRecommendationDraft(
            PRODUCT_ID, CHILD_ID, RecommendFixture.productA(), RULE_120, KEY_INPUT, QUESTION, EVIDENCE, null, null);
    }
}
