package com.bbcc.kidly.boundedcontext.recommend.app.companion;

import com.bbcc.kidly.boundedcontext.recommend.app.ai.AiResponseService;
import com.bbcc.kidly.boundedcontext.recommend.app.companion.dto.CompanionProductResult;
import com.bbcc.kidly.boundedcontext.recommend.app.companion.dto.CompanionProductsResult;
import com.bbcc.kidly.boundedcontext.recommend.app.companion.support.CompanionCandidateReader;
import com.bbcc.kidly.boundedcontext.recommend.app.companion.support.CompanionCandidates;
import com.bbcc.kidly.boundedcontext.recommend.app.port.companion.CompanionAiAnswer;
import com.bbcc.kidly.boundedcontext.recommend.app.port.companion.CompanionAiPicker;
import com.bbcc.kidly.boundedcontext.recommend.domain.ai.AiResponseKey;
import com.bbcc.kidly.boundedcontext.recommend.domain.ai.AiResponseType;
import com.bbcc.kidly.boundedcontext.recommend.domain.companion.CompanionAiPick;
import com.bbcc.kidly.boundedcontext.recommend.domain.companion.CompanionCandidate;
import com.bbcc.kidly.boundedcontext.recommend.domain.companion.CompanionPick;
import com.bbcc.kidly.boundedcontext.recommend.domain.companion.CompanionSelection;
import com.bbcc.kidly.boundedcontext.recommend.domain.recommended.RecommendedProduct;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 상품 상세에서 보고 있는 상품과 함께 사면 좋은 상품을 돌려준다.
 * 트랜잭션을 두지 않는다. AI를 트랜잭션 밖에서 불러 DB 연결을 붙잡지 않으려고, 읽기는 조각 빈이 트랜잭션 안에서 하고
 * AI 응답 서비스는 트랜잭션 안에서 부르면 예외를 던진다.
 */
@Service
public class CompanionProductService {

    // 프롬프트나 답의 모양(OpenAiCompanionAiPicker, CompanionAiAnswer)을 바꾸면 올린다.
    // 프롬프트에 자녀 정보를 쓰지 않으므로 재사용 키에도 자녀 정보를 넣지 않는다.
    private static final int PROMPT_VERSION = 3;

    private final CompanionCandidateReader candidateReader;
    private final AiResponseService aiResponseService;
    // OpenAI 키가 없으면 AI 선택 빈이 만들어지지 않으므로 Optional로 받는다.
    private final Optional<CompanionAiPicker> aiPicker;

    public CompanionProductService(
        CompanionCandidateReader candidateReader,
        AiResponseService aiResponseService,
        Optional<CompanionAiPicker> aiPicker
    ) {
        this.candidateReader = candidateReader;
        this.aiResponseService = aiResponseService;
        this.aiPicker = aiPicker;
    }

    public CompanionProductsResult recommend(Long memberId, Long productId, Long childId) {
        CompanionCandidates candidates = candidateReader.read(memberId, productId, childId);
        CompanionSelection selection = CompanionSelection.of(askAi(productId, candidates), candidates.ranked());
        List<CompanionProductResult> products = new ArrayList<>();
        for (CompanionPick pick : selection.picks()) {
            products.add(toResult(pick, candidates));
        }
        return new CompanionProductsResult(selection.source(), products);
    }

    // AI 결과가 없으면 null을 돌려주고, 고르는 쪽(CompanionSelection)이 규칙 결과로 대신한다
    private List<CompanionAiPick> askAi(Long productId, CompanionCandidates candidates) {
        // OpenAI 키가 없으면 AI 선택 빈이 없고, 후보가 없으면 고를 것이 없으므로 AI 공통 서비스를 부르지 않는다
        if (aiPicker.isEmpty() || candidates.ranked().isEmpty()) {
            return null;
        }
        CompanionAiPicker picker = aiPicker.get();
        List<Long> candidateProductIds = new ArrayList<>();
        for (CompanionCandidate candidate : candidates.ranked()) {
            candidateProductIds.add(candidate.product().productId());
        }
        AiResponseKey.Input input = new AiResponseKey.Input(productId, null, null, null, null, null, null,
            candidateProductIds, null, PROMPT_VERSION);
        // BOUGHT_TOGETHER는 함께 사면 좋은 상품 답을 뜻한다.
        Optional<CompanionAiAnswer> answer = aiResponseService.respond(AiResponseType.BOUGHT_TOGETHER, input,
            CompanionAiAnswer.class, () -> picker.pick(candidates.aiRequest()));
        if (answer.isEmpty()) {
            return null;
        }
        return answer.get().picks();
    }

    private CompanionProductResult toResult(CompanionPick pick, CompanionCandidates candidates) {
        RecommendedProduct product = pick.product();
        return new CompanionProductResult(product.productId(), product.name(), product.thumbnailUrl(),
            candidates.businessNames().get(product.sellerId()), product.minPrice(), product.recommendedSize(),
            pick.reason());
    }
}
