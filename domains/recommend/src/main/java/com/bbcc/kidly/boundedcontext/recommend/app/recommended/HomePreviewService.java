package com.bbcc.kidly.boundedcontext.recommend.app.recommended;

import com.bbcc.kidly.boundedcontext.recommend.app.recommended.dto.HomePreviewResult;
import com.bbcc.kidly.boundedcontext.recommend.app.recommended.dto.RecommendedProductResult;
import com.bbcc.kidly.boundedcontext.recommend.app.recommended.support.HomePreviewCandidateReader;
import com.bbcc.kidly.boundedcontext.recommend.domain.recommended.HomePreview;
import com.bbcc.kidly.boundedcontext.recommend.domain.recommended.HomePreviewSelector;
import com.bbcc.kidly.boundedcontext.recommend.domain.recommended.RecommendedProduct;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaSeller;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaSellerRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * 홈 화면에 보여 줄 맛보기 상품 4개를 만든다.
 */
// 이 서비스에는 @Transactional을 걸지 않는다. 다음 단계에서 이 서비스가 트랜잭션 밖에서만 부를 수 있는
// AI 공통 서비스를 부르기 때문이다. DB를 읽는 일은 각 읽기 빈이 자기 트랜잭션에서 한다
@Service
public class HomePreviewService {

    private final HomePreviewCandidateReader candidateReader;
    private final RecommendReplicaSellerRepository replicaSellerRepository;
    private final HomePreviewSelector selector = new HomePreviewSelector();

    public HomePreviewService(
        HomePreviewCandidateReader candidateReader, RecommendReplicaSellerRepository replicaSellerRepository) {
        this.candidateReader = candidateReader;
        this.replicaSellerRepository = replicaSellerRepository;
    }

    public HomePreviewResult preview(Long memberId, Long childId) {
        List<RecommendedProduct> candidates = candidateReader.read(memberId, childId);
        // AI가 아직 없으므로 규칙 결과만 돌려준다
        HomePreview preview = selector.select(candidates, null);
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

    // 맛보기 상품의 판매자를 중복 없이 모아 한 번에 읽는다. 복사본이 없는 판매자는 맵에 들어가지 않는다
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
