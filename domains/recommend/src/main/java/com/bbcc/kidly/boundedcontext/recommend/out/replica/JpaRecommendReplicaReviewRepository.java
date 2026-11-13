package com.bbcc.kidly.boundedcontext.recommend.out.replica;

import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaReview;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaReviewRepository;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.ReviewFit;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaRecommendReplicaReviewRepository
    extends JpaRepository<RecommendReplicaReview, Long>, RecommendReplicaReviewRepository {

    @Override
    @Query("""
        select r from RecommendReplicaReview r
        where r.orderItemId in :orderItemIds and r.visible = true and r.fit is not null
        """)
    List<RecommendReplicaReview> findVisibleFitsByOrderItemIds(@Param("orderItemIds") Collection<Long> orderItemIds);

    @Override
    @Query("""
        select count(r) from RecommendReplicaReview r
        where r.productId = :productId and r.fit = :fit and r.visible = true
        """)
    long countVisibleFits(@Param("productId") Long productId, @Param("fit") ReviewFit fit);
}
