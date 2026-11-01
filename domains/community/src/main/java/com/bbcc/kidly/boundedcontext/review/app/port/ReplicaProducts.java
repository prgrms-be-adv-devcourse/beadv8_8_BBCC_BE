package com.bbcc.kidly.boundedcontext.review.app.port;

import com.bbcc.kidly.boundedcontext.review.domain.ReplicaProduct;
import java.util.Optional;

/**
 * 상품 복사본 저장소. 구현은 out/persistence(Spring Data가 만든다).
 */
public interface ReplicaProducts {

    Optional<ReplicaProduct> findById(Long productId);

    ReplicaProduct save(ReplicaProduct replica);
}
