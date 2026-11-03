package com.bbcc.kidly.boundedcontext.review.app.port;

import com.bbcc.kidly.boundedcontext.review.domain.ReplicaProduct;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 상품 복사본 저장소. 구현은 out/persistence(Spring Data가 만든다).
 */
public interface ReplicaProducts {

    /**
     * 같은 상품을 읽기 전에 잡고, 트랜잭션 종료까지 반영을 직렬화한다.
     */
    void lockProduct(Long productId);

    Optional<ReplicaProduct> findById(Long productId);

    /**
     * 상품 ID 묶음으로 한 번에 읽는다(없는 상품은 빠진다).
     */
    List<ReplicaProduct> findAllByProductIdIn(Collection<Long> productIds);

    /**
     * 새 행으로만 넣고 바로 DB에 반영한다. 같은 상품 행이 이미 있으면(커밋 전이든 뒤든) 덮어쓰지 않고 PK 위반으로 실패한다.
     */
    ReplicaProduct insert(ReplicaProduct replica);
}
