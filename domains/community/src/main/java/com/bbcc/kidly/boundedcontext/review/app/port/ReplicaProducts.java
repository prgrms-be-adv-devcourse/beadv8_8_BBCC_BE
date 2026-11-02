package com.bbcc.kidly.boundedcontext.review.app.port;

import com.bbcc.kidly.boundedcontext.review.domain.ReplicaProduct;
import java.util.Optional;

/**
 * 상품 복사본 저장소. 구현은 out/persistence(Spring Data가 만든다).
 */
public interface ReplicaProducts {

    Optional<ReplicaProduct> findById(Long productId);

    /**
     * 새 행으로만 넣고 바로 DB에 반영한다. 같은 상품 행이 이미 있으면(커밋 전이든 뒤든) 덮어쓰지 않고 PK 위반으로 실패한다.
     */
    ReplicaProduct insert(ReplicaProduct replica);
}
