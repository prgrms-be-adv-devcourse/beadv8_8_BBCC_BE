package com.bbcc.kidly.boundedcontext.recommend.out.replica;

import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaSeller;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaSellerRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaRecommendReplicaSellerRepository
    extends JpaRepository<RecommendReplicaSeller, Long>, RecommendReplicaSellerRepository {
}
