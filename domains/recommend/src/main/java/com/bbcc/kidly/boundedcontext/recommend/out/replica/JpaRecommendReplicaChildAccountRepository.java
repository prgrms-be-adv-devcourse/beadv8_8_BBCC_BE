package com.bbcc.kidly.boundedcontext.recommend.out.replica;

import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaChildAccount;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaChildAccountRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaRecommendReplicaChildAccountRepository
    extends JpaRepository<RecommendReplicaChildAccount, Long>, RecommendReplicaChildAccountRepository {
}
