package com.bbcc.kidly.boundedcontext.recommend.out.replica;

import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaProduct;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaProductRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaRecommendReplicaProductRepository
    extends JpaRepository<RecommendReplicaProduct, Long>, RecommendReplicaProductRepository {
}
