package com.bbcc.kidly.boundedcontext.recommend.domain.replica;

import java.util.Optional;

public interface RecommendReplicaChildRepository {

    Optional<RecommendReplicaChild> findById(Long childProfileId);

    RecommendReplicaChild save(RecommendReplicaChild replicaChild);
}
