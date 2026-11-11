package com.bbcc.kidly.boundedcontext.recommend.app.recommended.support;

import com.bbcc.kidly.boundedcontext.recommend.app.port.ai.HomePreviewAiRequest;
import com.bbcc.kidly.boundedcontext.recommend.app.port.preference.PreferenceSourceReader;
import com.bbcc.kidly.boundedcontext.recommend.app.recommended.RecommendedProductRanking;
import com.bbcc.kidly.boundedcontext.recommend.domain.activity.ActivityType;
import com.bbcc.kidly.boundedcontext.recommend.domain.embedding.ProductEmbeddingText;
import com.bbcc.kidly.boundedcontext.recommend.domain.preference.PreferenceWeights;
import com.bbcc.kidly.boundedcontext.recommend.domain.preference.RecentActivity;
import com.bbcc.kidly.boundedcontext.recommend.domain.purchase.PurchaseRecordRepository;
import com.bbcc.kidly.boundedcontext.recommend.domain.recommended.ChildActivityEvidence;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 홈 맛보기 후보를 읽는다. 후보는 자녀에게 맞는 사이즈의 상품을 선호 점수 순서로 세운 앞 20개다.
 * AI에 보낼 요청에 필요한 값(자녀 활동으로 만든 근거 라벨, 판매 중인 색상, 상품 정보)도 함께 채운다.
 */
@Component
public class HomePreviewCandidateReader {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");
    private static final int PURCHASE_EVIDENCE_MONTHS = 3;
    private static final int PRODUCT_INFO_MAX_LENGTH = 200;

    private final RecommendReplicaChildRepository replicaChildRepository;
    private final RecommendReplicaProductRepository replicaProductRepository;
    private final RecommendedProductRanking ranking;
    private final PreferenceSourceReader preferenceSourceReader;
    private final PurchaseRecordRepository purchaseRecordRepository;
    private final HomePreviewSelector selector = new HomePreviewSelector();

    public HomePreviewCandidateReader(
        RecommendReplicaChildRepository replicaChildRepository,
        RecommendReplicaProductRepository replicaProductRepository,
        RecommendedProductRanking ranking,
        PreferenceSourceReader preferenceSourceReader,
        PurchaseRecordRepository purchaseRecordRepository) {
        this.replicaChildRepository = replicaChildRepository;
        this.replicaProductRepository = replicaProductRepository;
        this.ranking = ranking;
        this.preferenceSourceReader = preferenceSourceReader;
        this.purchaseRecordRepository = purchaseRecordRepository;
    }

    // 상품의 사이즈 줄 · 색상 · 카테고리 경로는 지연 로딩이므로 트랜잭션 안에서 읽어 값으로 옮긴다
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
        ChildActivityEvidence evidence = readEvidence(child.getChildProfileId(), now);
        HomePreviewAiRequest aiRequest = new HomePreviewAiRequest(
            toAiChild(child, now.toLocalDate()), toAiCandidates(products, evidence), now.toLocalDate());
        return new HomePreviewCandidates(child.getChildProfileId(), products, aiRequest);
    }

    private HomePreviewAiRequest.Child toAiChild(RecommendReplicaChild child, LocalDate today) {
        int ageMonths = (int) Period.between(child.getBirthDate(), today).toTotalMonths();
        return new HomePreviewAiRequest.Child(
            ageMonths, child.getHeightCm(), child.getWeightKg(), child.getBodyType(), child.getPreferredFit());
    }

    // 최근 30일 활동과 최근 3개월 구매를 세어 근거 라벨을 만들 재료로 모은다
    private ChildActivityEvidence readEvidence(Long childProfileId, LocalDateTime now) {
        Map<Long, Integer> categoryActivityCounts = new HashMap<>();
        Map<Long, Integer> categoryCartAddCounts = new HashMap<>();
        Map<String, Integer> brandActivityCounts = new HashMap<>();
        LocalDateTime activitySince = now.minusDays(PreferenceWeights.ACTIVITY_WINDOW_DAYS);
        for (RecentActivity activity : preferenceSourceReader.findChildActivities(childProfileId, activitySince, now)) {
            if (activity.removedFromCart()) {
                continue;
            }
            if (activity.categoryId() != null) {
                categoryActivityCounts.merge(activity.categoryId(), 1, Integer::sum);
                if (activity.type() == ActivityType.CART_ITEM_ADDED) {
                    categoryCartAddCounts.merge(activity.categoryId(), 1, Integer::sum);
                }
            }
            if (activity.brand() != null) {
                brandActivityCounts.merge(activity.brand(), 1, Integer::sum);
            }
        }
        Set<String> purchasedBrands = new HashSet<>();
        Set<Long> purchasedCategoryIds = new HashSet<>();
        collectPurchased(childProfileId, now, purchasedBrands, purchasedCategoryIds);
        return new ChildActivityEvidence(
            categoryActivityCounts, categoryCartAddCounts, brandActivityCounts, purchasedBrands, purchasedCategoryIds);
    }

    // 최근 3개월에 구매확정한 상품 복사본에서 브랜드와 카테고리를 모은다
    private void collectPurchased(Long childProfileId, LocalDateTime now, Set<String> brands, Set<Long> categoryIds) {
        List<Long> purchasedProductIds = purchaseRecordRepository.findProductIdsConfirmedSince(
            childProfileId, now.minusMonths(PURCHASE_EVIDENCE_MONTHS));
        for (RecommendReplicaProduct purchased : replicaProductRepository.findAllById(purchasedProductIds)) {
            if (purchased.getBrand() != null) {
                brands.add(purchased.getBrand());
            }
            if (purchased.getCategoryId() != null) {
                categoryIds.add(purchased.getCategoryId());
            }
        }
    }

    // 후보 상품 복사본을 한 번에 읽어 카테고리 경로 이름 · 근거 · 색상 · 상품 정보를 채운다
    private List<HomePreviewAiRequest.Candidate> toAiCandidates(
        List<RecommendedProduct> products, ChildActivityEvidence evidence) {
        List<Long> productIds = new ArrayList<>();
        for (RecommendedProduct product : products) {
            productIds.add(product.productId());
        }
        Map<Long, RecommendReplicaProduct> replicas = new HashMap<>();
        for (RecommendReplicaProduct replica : replicaProductRepository.findAllById(productIds)) {
            replicas.put(replica.getProductId(), replica);
        }
        List<HomePreviewAiRequest.Candidate> candidates = new ArrayList<>();
        for (RecommendedProduct product : products) {
            candidates.add(toAiCandidate(product, replicas.get(product.productId()), evidence));
        }
        return candidates;
    }

    private HomePreviewAiRequest.Candidate toAiCandidate(
        RecommendedProduct product, RecommendReplicaProduct replica, ChildActivityEvidence evidence) {
        if (replica == null) {
            return new HomePreviewAiRequest.Candidate(product.productId(), product.name(), product.brand(),
                List.of(), product.minPrice(), product.recommendedSize());
        }
        List<String> categoryPath = new ArrayList<>();
        for (RecommendReplicaCategoryPathEntry entry : replica.getCategoryPath()) {
            categoryPath.add(entry.getCategoryName());
        }
        // 카테고리 경로가 없으면 카테고리 근거를 만들지 않는다
        Long categoryId = null;
        String categoryName = null;
        if (!categoryPath.isEmpty()) {
            categoryId = replica.getCategoryId();
            categoryName = categoryPath.get(categoryPath.size() - 1);
        }
        return new HomePreviewAiRequest.Candidate(
            product.productId(), product.name(), product.brand(), categoryPath, product.minPrice(),
            product.recommendedSize(), evidence.labelsFor(categoryId, categoryName, product.brand()),
            replica.getColors(), productInfo(replica.getEmbeddingText()));
    }

    // 상품 정보 값과 설명을 한 줄로 잇고 code point 기준 200자로 자른다
    private String productInfo(String embeddingText) {
        if (embeddingText == null) {
            return null;
        }
        String detail = new ProductEmbeddingText(embeddingText).detailText();
        if (detail == null || detail.codePointCount(0, detail.length()) <= PRODUCT_INFO_MAX_LENGTH) {
            return detail;
        }
        return detail.substring(0, detail.offsetByCodePoints(0, PRODUCT_INFO_MAX_LENGTH));
    }
}
