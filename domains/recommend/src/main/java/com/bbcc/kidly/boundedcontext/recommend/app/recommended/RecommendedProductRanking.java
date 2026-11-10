package com.bbcc.kidly.boundedcontext.recommend.app.recommended;

import com.bbcc.kidly.boundedcontext.recommend.app.port.preference.PreferenceSnapshotStore;
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
    private final RecommendedProductSelector selector;
    private final RecommendedProductRanker ranker = new RecommendedProductRanker();

    public RecommendedProductRanking(
        RecommendReplicaProductRepository replicaProductRepository,
        PreferenceSnapshotStore snapshotStore,
        GrowthChart growthChart) {
        this.replicaProductRepository = replicaProductRepository;
        this.snapshotStore = snapshotStore;
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
        return ranker.rank(selected, combined, Map.of(), Set.of(), asOf);
    }
}
