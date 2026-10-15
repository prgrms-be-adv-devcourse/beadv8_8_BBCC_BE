package com.bbcc.kidly.boundedcontext.order.app.port;

import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaSeller;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 판매자 복사본 저장소. 구현은 out/persistence (Spring Data가 만든다).
 */
public interface SellerReplicaRepository {

    ReplicaSeller save(ReplicaSeller replicaSeller);

    Optional<ReplicaSeller> findBySellerId(Long sellerId);

    /**
     * 한 응답에 필요한 판매자를 한 번에 읽는다(판매자마다 조회하지 않음). 복사본이 없는 판매자는 빠진다.
     */
    List<ReplicaSeller> findAllBySellerIdIn(Collection<Long> sellerIds);
}
