package com.bbcc.kidly.boundedcontext.recommend.domain.replica;

import java.util.Optional;

public interface RecommendReplicaSellerRepository {

    Optional<RecommendReplicaSeller> findById(Long sellerId);

    RecommendReplicaSeller save(RecommendReplicaSeller replicaSeller);
}
