package com.bbcc.kidly.boundedcontext.recommend.app.recommended;

import com.bbcc.kidly.boundedcontext.recommend.app.recommended.dto.RecommendedProductResult;
import com.bbcc.kidly.boundedcontext.recommend.domain.recommended.RecommendedProduct;
import com.bbcc.kidly.boundedcontext.recommend.domain.recommended.RecommendedProductSelector;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendErrorCode;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaChild;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaChildRepository;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaProduct;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaProductRepository;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaSellerRepository;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.GrowthChart;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.SizeRecommender;
import com.bbcc.kidly.global.error.BusinessException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 자녀에게 맞는 사이즈로 살 수 있는 상품 목록을 만든다.
 */
@Service
public class RecommendedProductService {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final RecommendReplicaProductRepository replicaProductRepository;
    private final RecommendReplicaChildRepository replicaChildRepository;
    private final RecommendReplicaSellerRepository replicaSellerRepository;
    private final RecommendedProductSelector selector;

    public RecommendedProductService(
        RecommendReplicaProductRepository replicaProductRepository,
        RecommendReplicaChildRepository replicaChildRepository,
        RecommendReplicaSellerRepository replicaSellerRepository,
        GrowthChart growthChart) {
        this.replicaProductRepository = replicaProductRepository;
        this.replicaChildRepository = replicaChildRepository;
        this.replicaSellerRepository = replicaSellerRepository;
        this.selector = new RecommendedProductSelector(new SizeRecommender(growthChart));
    }

    // 상품의 사이즈 줄은 지연 로딩이므로 트랜잭션 안에서 읽어 계산한다
    @Transactional(readOnly = true)
    public Page<RecommendedProductResult> list(Long memberId, Long childId, Long categoryId, int page, int size) {
        RecommendReplicaChild child = replicaChildRepository.findById(childId)
            .orElseThrow(() -> new BusinessException(RecommendErrorCode.CHILD_NOT_FOUND));
        if (!child.isVisibleTo(memberId)) {
            throw new BusinessException(RecommendErrorCode.CHILD_NOT_FOUND);
        }

        List<RecommendReplicaProduct> products = replicaProductRepository.findAllOnSale(categoryId);
        List<RecommendedProduct> recommended =
            selector.select(child.toChildSizeInput(), products, LocalDate.now(SERVICE_ZONE));
        Pageable pageable = PageRequest.of(page, size);
        return new PageImpl<>(toPageContent(recommended, pageable), pageable, recommended.size());
    }

    // 요청한 페이지의 상품만 결과로 바꾼다. 시작 위치가 전체 개수를 넘으면 빈 목록이다
    private List<RecommendedProductResult> toPageContent(List<RecommendedProduct> recommended, Pageable pageable) {
        List<RecommendedProductResult> content = new ArrayList<>();
        long start = pageable.getOffset();
        long end = Math.min(start + pageable.getPageSize(), recommended.size());
        for (long i = start; i < end; i++) {
            RecommendedProduct product = recommended.get((int) i);
            // 판매자 상호는 아직 채우지 않는다
            content.add(new RecommendedProductResult(
                product.productId(), product.name(), product.thumbnailUrl(),
                null, product.minPrice(), product.recommendedSize()));
        }
        return content;
    }
}
