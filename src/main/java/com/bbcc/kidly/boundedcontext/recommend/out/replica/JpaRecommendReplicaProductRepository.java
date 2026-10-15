package com.bbcc.kidly.boundedcontext.recommend.out.replica;

import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaProduct;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaProductRepository;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaRecommendReplicaProductRepository
    extends JpaRepository<RecommendReplicaProduct, Long>, RecommendReplicaProductRepository {

    @Override
    @Query("""
        SELECT p FROM RecommendReplicaProduct p
        WHERE p.status = com.bbcc.kidly.shared.product.domain.ProductStatus.ON_SALE
          AND (:categoryId IS NULL OR p.categoryId = :categoryId)
        """)
    List<RecommendReplicaProduct> findAllOnSale(@Param("categoryId") Long categoryId);
}
