package com.bbcc.kidly.boundedcontext.recommend.app.companion.support;

import com.bbcc.kidly.boundedcontext.recommend.app.port.companion.CompanionAiProduct;
import com.bbcc.kidly.boundedcontext.recommend.app.port.companion.CompanionAiRequest;
import com.bbcc.kidly.boundedcontext.recommend.app.port.companion.CompanionSimilarityReader;
import com.bbcc.kidly.boundedcontext.recommend.app.port.recommended.RecommendExcludedProducts;
import com.bbcc.kidly.boundedcontext.recommend.domain.companion.CompanionCandidate;
import com.bbcc.kidly.boundedcontext.recommend.domain.companion.CompanionCandidateRanker;
import com.bbcc.kidly.boundedcontext.recommend.domain.preference.PreferenceWeights;
import com.bbcc.kidly.boundedcontext.recommend.domain.recommended.RecommendedProduct;
import com.bbcc.kidly.boundedcontext.recommend.domain.recommended.RecommendedProductSelector;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendErrorCode;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaCategoryPathEntry;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaChild;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaChildRepository;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaProduct;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaProductRepository;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaSeller;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaSellerRepository;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.GrowthChart;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.SizeRecommender;
import com.bbcc.kidly.global.error.BusinessException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 함께 사면 좋은 상품의 후보를 트랜잭션 안에서 읽어 규칙 순서로 정한다.
 * 상품의 사이즈 줄과 카테고리 경로는 지연 로딩이므로 이 트랜잭션 안에서 모두 읽는다.
 */
@Component
public class CompanionCandidateReader {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final RecommendReplicaChildRepository replicaChildRepository;
    private final RecommendReplicaProductRepository replicaProductRepository;
    private final RecommendReplicaSellerRepository replicaSellerRepository;
    private final RecommendExcludedProducts excludedProducts;
    private final CompanionSimilarityReader similarityReader;
    private final RecommendedProductSelector selector;
    private final CompanionCandidateRanker ranker = new CompanionCandidateRanker();

    public CompanionCandidateReader(
        RecommendReplicaChildRepository replicaChildRepository,
        RecommendReplicaProductRepository replicaProductRepository,
        RecommendReplicaSellerRepository replicaSellerRepository,
        RecommendExcludedProducts excludedProducts,
        CompanionSimilarityReader similarityReader,
        GrowthChart growthChart
    ) {
        this.replicaChildRepository = replicaChildRepository;
        this.replicaProductRepository = replicaProductRepository;
        this.replicaSellerRepository = replicaSellerRepository;
        this.excludedProducts = excludedProducts;
        this.similarityReader = similarityReader;
        this.selector = new RecommendedProductSelector(new SizeRecommender(growthChart));
    }

    @Transactional(readOnly = true)
    public CompanionCandidates read(Long memberId, Long productId, Long childId) {
        RecommendReplicaChild child = replicaChildRepository.findById(childId)
            .orElseThrow(() -> new BusinessException(RecommendErrorCode.CHILD_NOT_FOUND));
        if (!child.isVisibleTo(memberId)) {
            throw new BusinessException(RecommendErrorCode.CHILD_NOT_FOUND);
        }
        // 보고 있는 상품은 짝 종류를 정하는 데만 쓰므로 판매 상태는 보지 않는다.
        RecommendReplicaProduct viewed = replicaProductRepository.findById(productId)
            .orElseThrow(() -> new BusinessException(RecommendErrorCode.PRODUCT_NOT_FOUND));
        List<String> viewedPathNames = categoryPathNames(viewed);

        List<RecommendReplicaProduct> onSale = replicaProductRepository.findAllOnSale(null);
        List<RecommendedProduct> selected = selector.select(child.toChildSizeInput(), onSale,
            LocalDate.now(SERVICE_ZONE));
        List<RecommendedProduct> candidates = withoutExcluded(selected, memberId, child);
        List<CompanionCandidate> ranked = ranker.rank(viewedPathNames,
            toCandidates(productId, candidates, onSale));

        return new CompanionCandidates(toAiRequest(viewed, viewedPathNames, ranked), ranked,
            readBusinessNames(ranked));
    }

    // 추천은 결제로 장바구니가 비워지는 이벤트를 받지 않으므로, 30일 안에 담고 빼지 않은 상품을 장바구니에 있는 상품으로 본다.
    // 결제 뒤 구매확정 전인 상품도 이렇게 계속 빠진다.
    private List<RecommendedProduct> withoutExcluded(List<RecommendedProduct> selected, Long memberId,
        RecommendReplicaChild child) {
        LocalDateTime now = LocalDateTime.now(SERVICE_ZONE);
        Set<Long> excludedProductIds = excludedProducts.findIds(memberId, child.getChildProfileId(),
            now.minusDays(PreferenceWeights.CART_PRESENCE_DAYS), now);
        List<RecommendedProduct> candidates = new ArrayList<>();
        for (RecommendedProduct product : selected) {
            if (!excludedProductIds.contains(product.productId())) {
                candidates.add(product);
            }
        }
        return candidates;
    }

    private List<CompanionCandidate> toCandidates(Long viewedProductId, List<RecommendedProduct> candidates,
        List<RecommendReplicaProduct> onSale) {
        Map<Long, RecommendReplicaProduct> entitiesById = new HashMap<>();
        for (RecommendReplicaProduct entity : onSale) {
            entitiesById.put(entity.getProductId(), entity);
        }
        List<Long> candidateIds = new ArrayList<>();
        for (RecommendedProduct candidate : candidates) {
            candidateIds.add(candidate.productId());
        }
        // 보고 있는 상품이나 후보에 벡터가 없으면 그 후보는 유사도 없이(null) 둔다.
        Map<Long, Double> similarities = similarityReader.similarities(viewedProductId, candidateIds);
        List<CompanionCandidate> result = new ArrayList<>();
        for (RecommendedProduct candidate : candidates) {
            List<String> pathNames = categoryPathNames(entitiesById.get(candidate.productId()));
            result.add(new CompanionCandidate(candidate, pathNames, similarities.get(candidate.productId())));
        }
        return result;
    }

    private List<String> categoryPathNames(RecommendReplicaProduct product) {
        List<String> names = new ArrayList<>();
        for (RecommendReplicaCategoryPathEntry entry : product.getCategoryPath()) {
            names.add(entry.getCategoryName());
        }
        return names;
    }

    private CompanionAiRequest toAiRequest(RecommendReplicaProduct viewed, List<String> viewedPathNames,
        List<CompanionCandidate> ranked) {
        CompanionAiProduct viewedProduct = new CompanionAiProduct(viewed.getProductId(), viewed.getName(),
            viewedPathNames, viewed.getBrand());
        List<CompanionAiProduct> aiCandidates = new ArrayList<>();
        for (CompanionCandidate candidate : ranked) {
            RecommendedProduct product = candidate.product();
            aiCandidates.add(new CompanionAiProduct(product.productId(), product.name(),
                candidate.categoryPathNames(), product.brand()));
        }
        return new CompanionAiRequest(viewedProduct, aiCandidates);
    }

    // 순위에 든 상품의 판매자를 중복 없이 모아 한 번에 읽는다. 복사본이 없는 판매자는 맵에 들어가지 않는다
    private Map<Long, String> readBusinessNames(List<CompanionCandidate> ranked) {
        Set<Long> sellerIds = new HashSet<>();
        for (CompanionCandidate candidate : ranked) {
            sellerIds.add(candidate.product().sellerId());
        }
        Map<Long, String> businessNames = new HashMap<>();
        for (RecommendReplicaSeller seller : replicaSellerRepository.findAllById(sellerIds)) {
            businessNames.put(seller.getSellerId(), seller.getBusinessName());
        }
        return businessNames;
    }
}
