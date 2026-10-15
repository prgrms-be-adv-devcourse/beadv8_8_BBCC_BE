package com.bbcc.kidly.boundedcontext.recommend.domain.replica;

import java.util.List;
import java.util.Optional;

public interface RecommendReplicaProductRepository {

    Optional<RecommendReplicaProduct> findById(Long productId);

    RecommendReplicaProduct save(RecommendReplicaProduct replicaProduct);

    /**
     * 판매중인 상품 복사본을 읽는다. categoryId가 null이 아니면 카테고리가 같은 상품만 읽는다.
     */
    List<RecommendReplicaProduct> findAllOnSale(Long categoryId);
}
