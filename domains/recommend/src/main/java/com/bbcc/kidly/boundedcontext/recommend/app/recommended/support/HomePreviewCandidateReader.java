package com.bbcc.kidly.boundedcontext.recommend.app.recommended.support;

import com.bbcc.kidly.boundedcontext.recommend.app.port.ai.HomePreviewAiRequest;
import com.bbcc.kidly.boundedcontext.recommend.app.recommended.RecommendedProductRanking;
import com.bbcc.kidly.boundedcontext.recommend.domain.recommended.HomePreviewSelector;
import com.bbcc.kidly.boundedcontext.recommend.domain.recommended.RecommendedProduct;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendErrorCode;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaCategoryPathEntry;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaChild;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaChildRepository;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaProduct;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaProductRepository;
import com.bbcc.kidly.global.error.BusinessException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 홈 맛보기 후보를 읽는다. 후보는 자녀에게 맞는 사이즈의 상품을 선호 점수 순서로 세운 앞 20개다.
 * AI에 보낼 요청에 필요한 값도 함께 채운다.
 */
@Component
public class HomePreviewCandidateReader {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final RecommendReplicaChildRepository replicaChildRepository;
    private final RecommendReplicaProductRepository replicaProductRepository;
    private final RecommendedProductRanking ranking;
    private final HomePreviewSelector selector = new HomePreviewSelector();

    public HomePreviewCandidateReader(
        RecommendReplicaChildRepository replicaChildRepository,
        RecommendReplicaProductRepository replicaProductRepository,
        RecommendedProductRanking ranking) {
        this.replicaChildRepository = replicaChildRepository;
        this.replicaProductRepository = replicaProductRepository;
        this.ranking = ranking;
    }

    // 상품의 사이즈 줄과 카테고리 경로는 지연 로딩이므로 트랜잭션 안에서 읽어 값으로 옮긴다
    @Transactional(readOnly = true)
    public HomePreviewCandidates read(Long memberId, Long childId) {
        RecommendReplicaChild child = replicaChildRepository.findById(childId)
            .orElseThrow(() -> new BusinessException(RecommendErrorCode.CHILD_NOT_FOUND));
        if (!child.isVisibleTo(memberId)) {
            throw new BusinessException(RecommendErrorCode.CHILD_NOT_FOUND);
        }
        LocalDateTime now = LocalDateTime.now(SERVICE_ZONE);
        LocalDateTime asOf = now.truncatedTo(ChronoUnit.SECONDS);
        List<RecommendedProduct> products = selector.candidates(ranking.rank(memberId, child, null, asOf));
        HomePreviewAiRequest aiRequest =
            new HomePreviewAiRequest(toAiChild(child, now.toLocalDate()), toAiCandidates(products));
        return new HomePreviewCandidates(child.getChildProfileId(), products, aiRequest);
    }

    private HomePreviewAiRequest.Child toAiChild(RecommendReplicaChild child, LocalDate today) {
        int ageMonths = (int) Period.between(child.getBirthDate(), today).toTotalMonths();
        return new HomePreviewAiRequest.Child(
            ageMonths, child.getHeightCm(), child.getWeightKg(), child.getBodyType(), child.getPreferredFit());
    }

    // 후보 상품 복사본을 한 번에 읽어 카테고리 경로 이름을 채운다
    private List<HomePreviewAiRequest.Candidate> toAiCandidates(List<RecommendedProduct> products) {
        List<Long> productIds = new ArrayList<>();
        for (RecommendedProduct product : products) {
            productIds.add(product.productId());
        }
        Map<Long, List<String>> categoryPaths = new HashMap<>();
        for (RecommendReplicaProduct replica : replicaProductRepository.findAllById(productIds)) {
            List<String> names = new ArrayList<>();
            for (RecommendReplicaCategoryPathEntry entry : replica.getCategoryPath()) {
                names.add(entry.getCategoryName());
            }
            categoryPaths.put(replica.getProductId(), names);
        }
        List<HomePreviewAiRequest.Candidate> candidates = new ArrayList<>();
        for (RecommendedProduct product : products) {
            candidates.add(new HomePreviewAiRequest.Candidate(
                product.productId(), product.name(), product.brand(),
                categoryPaths.getOrDefault(product.productId(), List.of()),
                product.minPrice(), product.recommendedSize()));
        }
        return candidates;
    }
}
