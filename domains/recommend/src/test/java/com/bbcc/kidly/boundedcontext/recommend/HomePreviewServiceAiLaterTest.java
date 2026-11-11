package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendedProductFixture.BASE_TIME;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendedProductFixture.recommendedProductRegisteredAt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.bbcc.kidly.boundedcontext.recommend.app.ai.AiResponseService;
import com.bbcc.kidly.boundedcontext.recommend.app.port.ai.HomePreviewAiAnswer;
import com.bbcc.kidly.boundedcontext.recommend.app.port.ai.HomePreviewAiRequest;
import com.bbcc.kidly.boundedcontext.recommend.app.port.ai.HomePreviewPicker;
import com.bbcc.kidly.boundedcontext.recommend.app.recommended.HomePreviewService;
import com.bbcc.kidly.boundedcontext.recommend.app.recommended.dto.HomePreviewResult;
import com.bbcc.kidly.boundedcontext.recommend.app.recommended.dto.HomePreviewWithAi;
import com.bbcc.kidly.boundedcontext.recommend.app.recommended.support.HomePreviewCandidateReader;
import com.bbcc.kidly.boundedcontext.recommend.app.recommended.support.HomePreviewCandidates;
import com.bbcc.kidly.boundedcontext.recommend.domain.ai.AiResponseType;
import com.bbcc.kidly.boundedcontext.recommend.domain.recommended.RecommendedProduct;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaSellerRepository;
import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.childprofile.domain.PreferredFit;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 홈 맛보기가 AI 답을 기다리지 않고 규칙 결과를 바로 주고, AI 결과는 나중에 주는 동작을 확인한다(SSE 응답이 쓴다).
 */
@ExtendWith(MockitoExtension.class)
class HomePreviewServiceAiLaterTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long CHILD_ID = 11L;
    private static final HomePreviewAiRequest.Child CHILD = new HomePreviewAiRequest.Child(
        48, new BigDecimal("105.5"), new BigDecimal("17.2"), BodyType.NORMAL, PreferredFit.REGULAR);

    @Mock
    private HomePreviewCandidateReader candidateReader;

    @Mock
    private RecommendReplicaSellerRepository replicaSellerRepository;

    @Mock
    private AiResponseService aiResponseService;

    @Mock
    private HomePreviewPicker picker;

    @Test
    @DisplayName("HOME-03 기다리지 않는 맛보기는 규칙 상위 4개를 바로 담고, AI 답이 오면 AI가 고른 순서와 이유로 끝난다")
    void returnRuleNowAndAiLater() {
        givenCandidates(1L, 2L, 3L, 4L, 5L, 6L);
        HomePreviewAiAnswer answer = new HomePreviewAiAnswer(List.of(
            new HomePreviewAiAnswer.Pick(5L, "이유 5"), new HomePreviewAiAnswer.Pick(2L, "이유 2"),
            new HomePreviewAiAnswer.Pick(6L, "이유 6"), new HomePreviewAiAnswer.Pick(1L, "이유 1")));
        given(aiResponseService.respondAsync(eq(AiResponseType.HOME_PREVIEW), any(), eq(HomePreviewAiAnswer.class),
            any())).willReturn(CompletableFuture.completedFuture(Optional.of(answer)));

        HomePreviewWithAi result = service().previewWithAiLater(MEMBER_ID, CHILD_ID);

        assertThat(result.rule().source()).isEqualTo("RULE");
        assertThat(result.rule().items()).extracting(item -> item.product().productId())
            .containsExactly(1L, 2L, 3L, 4L);
        HomePreviewResult ai = result.ai().join().orElseThrow();
        assertThat(ai.source()).isEqualTo("AI");
        assertThat(ai.items()).extracting(item -> item.product().productId()).containsExactly(5L, 2L, 6L, 1L);
        assertThat(ai.items()).extracting(HomePreviewResult.Item::reason)
            .containsExactly("이유 5", "이유 2", "이유 6", "이유 1");
    }

    @Test
    @DisplayName("HOME-02 기다리지 않는 맛보기에서 AI 답을 얻지 못하면 AI 결과는 빈 값으로 끝난다")
    void completeAiEmptyWhenNoAiAnswer() {
        givenCandidates(1L, 2L, 3L, 4L, 5L, 6L);
        given(aiResponseService.respondAsync(eq(AiResponseType.HOME_PREVIEW), any(), eq(HomePreviewAiAnswer.class),
            any())).willReturn(CompletableFuture.completedFuture(Optional.empty()));

        HomePreviewWithAi result = service().previewWithAiLater(MEMBER_ID, CHILD_ID);

        assertThat(result.rule().items()).hasSize(4);
        assertThat(result.ai().join()).isEmpty();
    }

    private HomePreviewService service() {
        return new HomePreviewService(candidateReader, replicaSellerRepository, aiResponseService,
            Optional.of(picker));
    }

    // 상품 ID 순서가 선호 점수 순서인 후보를 읽게 한다
    private void givenCandidates(Long... productIds) {
        List<RecommendedProduct> products = new ArrayList<>();
        List<HomePreviewAiRequest.Candidate> aiCandidates = new ArrayList<>();
        for (Long productId : productIds) {
            RecommendedProduct product = recommendedProductRegisteredAt(productId, BASE_TIME);
            products.add(product);
            aiCandidates.add(new HomePreviewAiRequest.Candidate(productId, product.name(), product.brand(),
                List.of("의류", "상의"), product.minPrice(), product.recommendedSize()));
        }
        given(candidateReader.read(MEMBER_ID, CHILD_ID)).willReturn(
            new HomePreviewCandidates(CHILD_ID, products, new HomePreviewAiRequest(CHILD, aiCandidates)));
    }
}
