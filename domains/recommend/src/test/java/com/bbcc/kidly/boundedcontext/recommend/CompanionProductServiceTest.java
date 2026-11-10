package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.bbcc.kidly.boundedcontext.recommend.app.ai.AiResponseService;
import com.bbcc.kidly.boundedcontext.recommend.app.companion.CompanionProductService;
import com.bbcc.kidly.boundedcontext.recommend.app.companion.dto.CompanionProductResult;
import com.bbcc.kidly.boundedcontext.recommend.app.companion.dto.CompanionProductsResult;
import com.bbcc.kidly.boundedcontext.recommend.app.companion.support.CompanionCandidateReader;
import com.bbcc.kidly.boundedcontext.recommend.app.companion.support.CompanionCandidates;
import com.bbcc.kidly.boundedcontext.recommend.app.port.companion.CompanionAiAnswer;
import com.bbcc.kidly.boundedcontext.recommend.app.port.companion.CompanionAiPicker;
import com.bbcc.kidly.boundedcontext.recommend.app.port.companion.CompanionAiProduct;
import com.bbcc.kidly.boundedcontext.recommend.app.port.companion.CompanionAiRequest;
import com.bbcc.kidly.boundedcontext.recommend.domain.ai.AiResponseKey;
import com.bbcc.kidly.boundedcontext.recommend.domain.ai.AiResponseType;
import com.bbcc.kidly.boundedcontext.recommend.domain.companion.CompanionAiPick;
import com.bbcc.kidly.boundedcontext.recommend.domain.companion.CompanionCandidate;
import com.bbcc.kidly.boundedcontext.recommend.domain.companion.CompanionSource;
import com.bbcc.kidly.boundedcontext.recommend.domain.recommended.RecommendedProduct;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompanionProductServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long CHILD_ID = 10L;
    private static final Long VIEWED_PRODUCT_ID = 7001L;
    private static final Long SELLER_ID = 100L;
    private static final String BUSINESS_NAME = "키즈랩";
    private static final LocalDateTime SALE_STARTED_AT = LocalDateTime.of(2026, 11, 1, 10, 0);

    @Mock
    private CompanionCandidateReader candidateReader;

    @Mock
    private AiResponseService aiResponseService;

    @Mock
    private CompanionAiPicker aiPicker;

    @Test
    @DisplayName("PAIR-06 AI 답이 있으면 AI가 고른 순서와 이유로 돌려주고 출처는 AI다. AI는 보고 있는 상품과 후보 순서로 만든 입력으로 부른다")
    void useAiAnswerWithCandidateInput() {
        CompanionCandidates candidates = candidates(6);
        given(candidateReader.read(MEMBER_ID, VIEWED_PRODUCT_ID, CHILD_ID)).willReturn(candidates);
        CompanionAiAnswer answer = new CompanionAiAnswer(List.of(
            new CompanionAiPick(5L, "같은 색 계열이라 함께 입기 좋다"),
            new CompanionAiPick(2L, "가을에 겹쳐 입기 좋다")));
        ArgumentCaptor<AiResponseKey.Input> input = ArgumentCaptor.forClass(AiResponseKey.Input.class);
        ArgumentCaptor<Supplier<CompanionAiAnswer>> call = supplierCaptor();
        given(aiResponseService.respond(eq(AiResponseType.BOUGHT_TOGETHER), input.capture(),
            eq(CompanionAiAnswer.class), call.capture())).willReturn(Optional.of(answer));

        CompanionProductsResult result = service(Optional.of(aiPicker)).recommend(MEMBER_ID, VIEWED_PRODUCT_ID,
            CHILD_ID);

        assertThat(result.source()).isEqualTo(CompanionSource.AI);
        assertThat(result.products()).extracting(CompanionProductResult::productId).containsExactly(5L, 2L, 1L, 3L);
        assertThat(result.products()).extracting(CompanionProductResult::reason)
            .containsExactly("같은 색 계열이라 함께 입기 좋다", "가을에 겹쳐 입기 좋다", null, null);
        assertThat(result.products().getFirst()).isEqualTo(
            new CompanionProductResult(5L, "상품 5", "https://img/5.jpg", BUSINESS_NAME, 10_000L, "110",
                "같은 색 계열이라 함께 입기 좋다"));
        // 프롬프트에 자녀 정보를 쓰지 않으므로 재사용 키에도 자녀 정보를 넣지 않는다
        assertThat(input.getValue()).isEqualTo(new AiResponseKey.Input(VIEWED_PRODUCT_ID, null, null, null, null,
            null, null, List.of(1L, 2L, 3L, 4L, 5L, 6L), null, 1));
        call.getValue().get();
        verify(aiPicker).pick(candidates.aiRequest());
    }

    @Test
    @DisplayName("PAIR-06 AI 결과가 없으면(실패 · 제한 시간 초과 · 하루 상한) 규칙 상위 4개를 이유 없이 돌려주고 출처는 RULE이다")
    void useRuleTopFourWhenAiAnswerIsEmpty() {
        given(candidateReader.read(MEMBER_ID, VIEWED_PRODUCT_ID, CHILD_ID)).willReturn(candidates(6));
        given(aiResponseService.respond(eq(AiResponseType.BOUGHT_TOGETHER), any(), eq(CompanionAiAnswer.class), any()))
            .willReturn(Optional.empty());

        CompanionProductsResult result = service(Optional.of(aiPicker)).recommend(MEMBER_ID, VIEWED_PRODUCT_ID,
            CHILD_ID);

        assertThat(result.source()).isEqualTo(CompanionSource.RULE);
        assertThat(result.products()).extracting(CompanionProductResult::productId).containsExactly(1L, 2L, 3L, 4L);
        assertThat(result.products()).extracting(CompanionProductResult::reason).containsOnlyNulls();
    }

    private CompanionProductService service(Optional<CompanionAiPicker> picker) {
        return new CompanionProductService(candidateReader, aiResponseService, picker);
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<Supplier<CompanionAiAnswer>> supplierCaptor() {
        return ArgumentCaptor.forClass((Class<Supplier<CompanionAiAnswer>>) (Class<?>) Supplier.class);
    }

    // 상품 ID 1부터 count까지를 규칙 순서로 둔 후보와, 그 후보로 만든 AI 요청이다
    private static CompanionCandidates candidates(int count) {
        List<CompanionCandidate> ranked = new ArrayList<>();
        List<CompanionAiProduct> aiCandidates = new ArrayList<>();
        for (long id = 1; id <= count; id++) {
            RecommendedProduct product = new RecommendedProduct(id, SELLER_ID, "상품 " + id, "https://img/" + id + ".jpg",
                10_000L, "110", 1L, "브랜드", SALE_STARTED_AT, SALE_STARTED_AT);
            ranked.add(new CompanionCandidate(product, List.of("의류", "하의"), 0.5));
            aiCandidates.add(new CompanionAiProduct(id, "상품 " + id, List.of("의류", "하의"), "브랜드"));
        }
        CompanionAiProduct viewed = new CompanionAiProduct(VIEWED_PRODUCT_ID, "데일리 맨투맨", List.of("의류", "상의"),
            "키즈랩");
        return new CompanionCandidates(new CompanionAiRequest(viewed, aiCandidates), ranked,
            Map.of(SELLER_ID, BUSINESS_NAME));
    }
}
