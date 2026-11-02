package com.bbcc.kidly.boundedcontext.review.out.store;

import com.bbcc.kidly.boundedcontext.review.domain.ReplicaProduct;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * {@link ReplicaProductInsert} 구현: persist 뒤 바로 flush해 PK 위반을 이 호출에서 드러낸다.
 * 예외 변환(DataIntegrityViolationException)은 저장소 프록시가 한다.
 */
class ReplicaProductInsertImpl implements ReplicaProductInsert {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public ReplicaProduct insert(ReplicaProduct replica) {
        entityManager.persist(replica);
        entityManager.flush();
        return replica;
    }
}
