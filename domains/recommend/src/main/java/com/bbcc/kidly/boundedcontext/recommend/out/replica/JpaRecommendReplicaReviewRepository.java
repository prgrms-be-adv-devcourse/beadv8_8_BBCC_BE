package com.bbcc.kidly.boundedcontext.recommend.out.replica;

import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaReview;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaReviewRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaRecommendReplicaReviewRepository
    extends JpaRepository<RecommendReplicaReview, Long>, RecommendReplicaReviewRepository {
}
