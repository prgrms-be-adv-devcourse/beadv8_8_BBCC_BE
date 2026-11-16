package com.bbcc.kidly.boundedcontext.recommend.domain.replica;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RecommendReplicaProductRepository {

    Optional<RecommendReplicaProduct> findById(Long productId);

    List<RecommendReplicaProduct> findAllById(Iterable<Long> productIds);

    RecommendReplicaProduct save(RecommendReplicaProduct replicaProduct);

    /**
     * 판매중인 상품 복사본을 읽는다. categoryId가 null이 아니면 카테고리가 같은 상품만 읽는다.
     */
    List<RecommendReplicaProduct> findAllOnSale(Long categoryId);

    /**
     * 기준 시각 뒤에 고쳐진 상품 복사본을 판매 상태와 상관없이 읽는다.
     * 메모리에 둔 값에서 판매가 끝난 상품을 빼려면 판매중이 아니게 바뀐 상품도 알아야 하기 때문이다.
     */
    List<RecommendReplicaProduct> findAllChangedAfter(LocalDateTime since);

    /**
     * 판매중인 상품 복사본의 수를 센다.
     */
    long countOnSale();
}
