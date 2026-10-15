package com.bbcc.kidly.boundedcontext.recommend.domain.replica;

import java.util.Optional;

public interface RecommendReplicaProductRepository {

    Optional<RecommendReplicaProduct> findById(Long productId);

    RecommendReplicaProduct save(RecommendReplicaProduct replicaProduct);
}
