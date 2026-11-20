package com.bbcc.kidly.boundedcontext.recommend.domain.replica;

import java.util.Optional;

public interface RecommendReplicaChildAccountRepository {

    Optional<RecommendReplicaChildAccount> findById(Long accountId);

    RecommendReplicaChildAccount save(RecommendReplicaChildAccount account);
}
