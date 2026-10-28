package com.bbcc.kidly.boundedcontext.recommend.domain.replica;

import java.util.List;
import java.util.Optional;

public interface RecommendReplicaSellerRepository {

    Optional<RecommendReplicaSeller> findById(Long sellerId);

    List<RecommendReplicaSeller> findAllById(Iterable<Long> sellerIds);

    RecommendReplicaSeller save(RecommendReplicaSeller replicaSeller);
}
