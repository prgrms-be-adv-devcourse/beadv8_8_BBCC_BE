package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.bbcc.kidly.boundedcontext.recommend.app.ai.AiResponseService;
import com.bbcc.kidly.boundedcontext.recommend.app.port.size.AiSizeChooser;
import com.bbcc.kidly.boundedcontext.recommend.app.port.size.SizeAiQuestion;
import com.bbcc.kidly.boundedcontext.recommend.app.size.SizeRecommendationService;
import com.bbcc.kidly.boundedcontext.recommend.app.size.dto.SizeRecommendationResult;
import com.bbcc.kidly.boundedcontext.recommend.app.size.support.SizeRecommendationDraft;
import com.bbcc.kidly.boundedcontext.recommend.app.size.support.SizeRecommendationReader;
import com.bbcc.kidly.boundedcontext.recommend.domain.ai.AiResponseKey;
import com.bbcc.kidly.boundedcontext.recommend.domain.ai.AiResponseType;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.AiSizeAnswer;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.Fit;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.SizeNotice;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.SizeRecommendation;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
            List.of(new AiSizeAnswer.Reason("HEIGHT", "키 114cm는 110의 권장 키 안이에요.", null)));
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

    private SizeRecommendationService service() {
        return new SizeRecommendationService(reader, aiResponseService, Optional.of(aiSizeChooser));
    }

    private static SizeRecommendationDraft draftOf(SizeRecommendation rule) {
        return new SizeRecommendationDraft(
            PRODUCT_ID, CHILD_ID, RecommendFixture.productA(), rule, KEY_INPUT, QUESTION);
    }

    // AI 응답 서비스가 저장된 답 없이 넘겨받은 호출을 그대로 실행한 것처럼 한다
    @SuppressWarnings("unchecked")
    private void givenAiResponseServiceCallsChooser() {
        given(aiResponseService.respond(
            eq(AiResponseType.SIZE_RECOMMENDATION), eq(KEY_INPUT), eq(AiSizeAnswer.class), any()))
            .willAnswer(invocation -> Optional.ofNullable(((Supplier<AiSizeAnswer>) invocation.getArgument(3)).get()));
    }
}
