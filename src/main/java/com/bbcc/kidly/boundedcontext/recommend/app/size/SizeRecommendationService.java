package com.bbcc.kidly.boundedcontext.recommend.app.size;

import com.bbcc.kidly.boundedcontext.recommend.app.size.dto.SizeRecommendationResult;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaChild;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaChildRepository;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaProduct;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaProductRepository;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.GrowthChart;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.SizeNotice;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.SizeRecommendation;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.SizeRecommendationErrorCode;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.SizeRecommender;
import com.bbcc.kidly.global.error.BusinessException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SizeRecommendationService {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final RecommendReplicaProductRepository replicaProductRepository;
    private final RecommendReplicaChildRepository replicaChildRepository;
    private final SizeRecommender sizeRecommender;

    public SizeRecommendationService(
        RecommendReplicaProductRepository replicaProductRepository,
        RecommendReplicaChildRepository replicaChildRepository,
        GrowthChart growthChart) {
        this.replicaProductRepository = replicaProductRepository;
        this.replicaChildRepository = replicaChildRepository;
        this.sizeRecommender = new SizeRecommender(growthChart);
    }

    // 상품의 사이즈 줄은 지연 로딩이므로 트랜잭션 안에서 읽어 계산한다
    @Transactional(readOnly = true)
    public SizeRecommendationResult recommend(Long memberId, Long productId, Long childId) {
        RecommendReplicaProduct product = replicaProductRepository.findById(productId)
            .orElseThrow(() -> new BusinessException(SizeRecommendationErrorCode.PRODUCT_NOT_FOUND));
        RecommendReplicaChild child = replicaChildRepository.findById(childId)
            .orElseThrow(() -> new BusinessException(SizeRecommendationErrorCode.CHILD_NOT_FOUND));
        if (!child.isVisibleTo(memberId)) {
            throw new BusinessException(SizeRecommendationErrorCode.CHILD_NOT_FOUND);
        }

        SizeRecommendation recommendation = sizeRecommender.recommend(
            child.toChildSizeInput(), product.toProductSizes(), LocalDate.now(SERVICE_ZONE));
        return toResult(productId, childId, recommendation);
    }

    private SizeRecommendationResult toResult(Long productId, Long childId, SizeRecommendation recommendation) {
        String fit = null;
        if (recommendation.getFit() != null) {
            fit = recommendation.getFit().name();
        }
        List<SizeRecommendationResult.Notice> notices = new ArrayList<>();
        for (SizeNotice notice : recommendation.getNotices()) {
            notices.add(new SizeRecommendationResult.Notice(notice.name()));
        }
        // 지난 구매 사이즈는 아직 채우지 않는다
        return new SizeRecommendationResult(
            productId, childId, recommendation.getRecommendedSize(), fit,
            notices, recommendation.getCandidateSizes(), null);
    }
}
