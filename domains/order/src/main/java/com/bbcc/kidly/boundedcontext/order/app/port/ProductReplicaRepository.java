package com.bbcc.kidly.boundedcontext.order.app.port;

import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaProduct;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 상품 복사본 저장소. 구현은 out/persistence (Spring Data가 만든다).
 */
public interface ProductReplicaRepository {

    ReplicaProduct save(ReplicaProduct replicaProduct);

    Optional<ReplicaProduct> findByOptionId(Long optionId);

    List<ReplicaProduct> findAllByOptionIdIn(Collection<Long> optionIds);

    List<ReplicaProduct> findAllByProductId(Long productId);
}
