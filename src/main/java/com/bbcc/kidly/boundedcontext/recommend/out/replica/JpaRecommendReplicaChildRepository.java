package com.bbcc.kidly.boundedcontext.recommend.out.replica;

import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaChild;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaChildRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaRecommendReplicaChildRepository
    extends JpaRepository<RecommendReplicaChild, Long>, RecommendReplicaChildRepository {
}
