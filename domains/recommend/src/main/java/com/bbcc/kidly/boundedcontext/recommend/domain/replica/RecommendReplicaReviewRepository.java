package com.bbcc.kidly.boundedcontext.recommend.domain.replica;

import java.util.Optional;

public interface RecommendReplicaReviewRepository {

    Optional<RecommendReplicaReview> findById(Long reviewId);

    RecommendReplicaReview save(RecommendReplicaReview replicaReview);
}
