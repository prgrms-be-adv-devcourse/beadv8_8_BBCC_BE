package com.bbcc.kidly.boundedcontext.recommend.app.recommended;

import com.bbcc.kidly.boundedcontext.recommend.app.ai.AiResponseService;
import com.bbcc.kidly.boundedcontext.recommend.app.port.ai.HomePreviewAiAnswer;
import com.bbcc.kidly.boundedcontext.recommend.app.port.ai.HomePreviewAiRequest;
import com.bbcc.kidly.boundedcontext.recommend.app.port.ai.HomePreviewPicker;
import com.bbcc.kidly.boundedcontext.recommend.app.recommended.dto.HomePreviewResult;
import com.bbcc.kidly.boundedcontext.recommend.app.recommended.dto.RecommendedProductResult;
import com.bbcc.kidly.boundedcontext.recommend.app.recommended.support.HomePreviewCandidateReader;
import com.bbcc.kidly.boundedcontext.recommend.app.recommended.support.HomePreviewCandidates;
import com.bbcc.kidly.boundedcontext.recommend.domain.ai.AiResponseKey;
import com.bbcc.kidly.boundedcontext.recommend.domain.ai.AiResponseType;
import com.bbcc.kidly.boundedcontext.recommend.domain.recommended.HomePreview;
import com.bbcc.kidly.boundedcontext.recommend.domain.recommended.HomePreviewPick;
import com.bbcc.kidly.boundedcontext.recommend.domain.recommended.HomePreviewSelector;
import com.bbcc.kidly.boundedcontext.recommend.domain.recommended.RecommendedProduct;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaSeller;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaSellerRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * 홈 화면에 보여 줄 맛보기 상품 4개를 만든다.
 */
// 이 서비스는 트랜잭션 밖에서만 부를 수 있는 AI 공통 서비스를 부르므로 @Transactional을 걸지 않는다
@Service
public class HomePreviewService {

    // AI 답의 칸 이름이나 뜻, 프롬프트를 바꾸면 올려서 이전 버전의 저장된 답을 다시 쓰지 않게 한다.
    // 프롬프트 문구는 OpenAiHomePreviewPicker에 있다
    private static final int PROMPT_VERSION = 1;

    private final HomePreviewCandidateReader candidateReader;
    private final RecommendReplicaSellerRepository replicaSellerRepository;
    private final AiResponseService aiResponseService;
    // OpenAI 키가 없으면 picker 빈이 없다
    private final Optional<HomePreviewPicker> picker;
    private final HomePreviewSelector selector = new HomePreviewSelector();

    public HomePreviewService(
        HomePreviewCandidateReader candidateReader, RecommendReplicaSellerRepository replicaSellerRepository,
        AiResponseService aiResponseService, Optional<HomePreviewPicker> picker) {
        this.candidateReader = candidateReader;
        this.replicaSellerRepository = replicaSellerRepository;
        this.aiResponseService = aiResponseService;
        this.picker = picker;
    }

    public HomePreviewResult preview(Long memberId, Long childId) {
        HomePreviewCandidates candidates = candidateReader.read(memberId, childId);
        HomePreview preview = selector.select(candidates.products(), askAi(candidates));
        Map<Long, String> businessNames = readBusinessNames(preview.items());
        List<HomePreviewResult.Item> items = new ArrayList<>();
        for (HomePreview.Item item : preview.items()) {
            RecommendedProduct product = item.product();
            items.add(new HomePreviewResult.Item(
                new RecommendedProductResult(
                    product.productId(), product.name(), product.thumbnailUrl(),
                    businessNames.get(product.sellerId()), product.minPrice(), product.recommendedSize()),
                item.reason()));
        }
        return new HomePreviewResult(preview.source().name(), items);
    }

    // AI를 쓸 수 없거나 답이 없으면 null이고, 그러면 규칙 결과가 된다
    private List<HomePreviewPick> askAi(HomePreviewCandidates candidates) {
        if (picker.isEmpty() || candidates.products().isEmpty()) {
            return null;
        }
        HomePreviewAiRequest request = candidates.aiRequest();
        Optional<HomePreviewAiAnswer> answer = aiResponseService.respond(
            AiResponseType.HOME_PREVIEW, key(candidates), HomePreviewAiAnswer.class,
            () -> picker.get().pick(request));
        if (answer.isEmpty() || answer.get().picks() == null) {
            return null;
        }
        List<HomePreviewPick> picks = new ArrayList<>();
        for (HomePreviewAiAnswer.Pick pick : answer.get().picks()) {
            if (pick == null) {
                picks.add(null);
            } else {
                picks.add(new HomePreviewPick(pick.productId(), pick.reason()));
            }
        }
        return picks;
    }

    // 장바구니 ID는 키에 넣지 않는다. 후보에서 이미 장바구니 상품을 빼므로, 담기가 후보를 바꾸면 키도 바뀐다
    private AiResponseKey.Input key(HomePreviewCandidates candidates) {
        HomePreviewAiRequest.Child child = candidates.aiRequest().child();
        List<Long> productIds = new ArrayList<>();
        for (RecommendedProduct product : candidates.products()) {
            productIds.add(product.productId());
        }
        return new AiResponseKey.Input(null, candidates.childProfileId(), child.ageMonths(), child.heightCm(),
            child.weightKg(), child.bodyType(), child.preferredFit(), productIds, null, PROMPT_VERSION);
    }

    // 맛보기 상품의 판매자를 중복 없이 모아 한 번에 읽는다. 복사본이 없는 판매자는 맵에 들어가지 않는다.
    // 판매자 상호는 리포지토리 기본 메서드가 자기 읽기 트랜잭션에서 읽고, 기본 칸만 읽어 지연 로딩이 없다
    private Map<Long, String> readBusinessNames(List<HomePreview.Item> items) {
        Set<Long> sellerIds = new HashSet<>();
        for (HomePreview.Item item : items) {
            sellerIds.add(item.product().sellerId());
        }
        Map<Long, String> businessNames = new HashMap<>();
        for (RecommendReplicaSeller seller : replicaSellerRepository.findAllById(sellerIds)) {
            businessNames.put(seller.getSellerId(), seller.getBusinessName());
        }
        return businessNames;
    }
}
