package com.bbcc.kidly.boundedcontext.recommend.app.recommended;

import com.bbcc.kidly.boundedcontext.recommend.app.port.preference.PreferenceSnapshotStore;
import com.bbcc.kidly.boundedcontext.recommend.app.port.preference.TasteSimilarityReader;
import com.bbcc.kidly.boundedcontext.recommend.domain.preference.PreferenceSnapshot;
import com.bbcc.kidly.boundedcontext.recommend.domain.recommended.RecommendedProduct;
import com.bbcc.kidly.boundedcontext.recommend.domain.recommended.RecommendedProductRanker;
import com.bbcc.kidly.boundedcontext.recommend.domain.recommended.RecommendedProductSelector;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaChild;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaProduct;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaProductRepository;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.GrowthChart;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.SizeRecommender;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 자녀에게 맞는 사이즈로 살 수 있는 상품을 저장된 선호 점수 순서로 정렬한다.
 * 더보기와 같은 순서를 다른 목록이 다시 쓸 수 있도록 정렬까지를 한 곳에 모았다.
 */
@Component
public class RecommendedProductRanking {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final RecommendReplicaProductRepository replicaProductRepository;
    private final PreferenceSnapshotStore snapshotStore;
    private final TasteSimilarityReader tasteSimilarityReader;
    private final RecommendedProductSelector selector;
    private final RecommendedProductRanker ranker = new RecommendedProductRanker();

    public RecommendedProductRanking(
        RecommendReplicaProductRepository replicaProductRepository,
        PreferenceSnapshotStore snapshotStore,
        TasteSimilarityReader tasteSimilarityReader,
        GrowthChart growthChart) {
        this.replicaProductRepository = replicaProductRepository;
        this.snapshotStore = snapshotStore;
        this.tasteSimilarityReader = tasteSimilarityReader;
        this.selector = new RecommendedProductSelector(new SizeRecommender(growthChart));
    }

    // 상품의 사이즈 줄은 지연 로딩이므로 호출하는 쪽의 트랜잭션 안에서 읽어 계산한다
    public List<RecommendedProduct> rank(Long memberId, RecommendReplicaChild child, Long categoryId,
        LocalDateTime asOf) {
        List<RecommendReplicaProduct> products = replicaProductRepository.findAllOnSale(categoryId);
        List<RecommendedProduct> selected =
            selector.select(child.toChildSizeInput(), products, LocalDate.now(SERVICE_ZONE));
        PreferenceSnapshot combined = PreferenceSnapshot.combine(
            snapshotStore.findChild(child.getChildProfileId()), snapshotStore.findMember(memberId));
        return ranker.rank(selected, combined, readSimilarities(combined, selected), Set.of(), asOf);
    }

    // 취향 벡터가 없으면 유사도를 쓰지 않으므로 상품 벡터를 읽지 않는다
    private Map<Long, Double> readSimilarities(PreferenceSnapshot snapshot, List<RecommendedProduct> candidates) {
        if (snapshot.tasteVector() == null) {
            return Map.of();
        }
        List<Long> productIds = new ArrayList<>();
        for (RecommendedProduct candidate : candidates) {
            productIds.add(candidate.productId());
        }
        return tasteSimilarityReader.similarities(snapshot.tasteVector().embedding(), productIds);
    }
}
