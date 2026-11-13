package com.bbcc.kidly.boundedcontext.recommend.domain.replica;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RecommendReplicaReviewRepository {

    Optional<RecommendReplicaReview> findById(Long reviewId);

    RecommendReplicaReview save(RecommendReplicaReview replicaReview);

    /**
     * 주문상품들에 남긴 리뷰 가운데 공개되고 핏을 남긴 리뷰만 찾는다. 지난 구매에 그 구매의 리뷰 핏을 붙일 때 쓴다.
     */
    List<RecommendReplicaReview> findVisibleFitsByOrderItemIds(Collection<Long> orderItemIds);

    /**
     * 상품에 공개된 리뷰 가운데 이 핏을 남긴 리뷰 수를 센다. 상품 핏 성향을 만들 때 쓴다.
     */
    long countVisibleFits(Long productId, ReviewFit fit);
}
