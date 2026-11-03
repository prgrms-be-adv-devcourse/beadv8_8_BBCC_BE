package com.bbcc.kidly.boundedcontext.review.out.store;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 아직 없는 상품도 잠그는 상품 ID별 트랜잭션 advisory lock. 커밋 · 롤백 때 DB가 해제한다.
 * 종류가 다른 잠금과 키를 구분하며, 해시 충돌은 불필요한 대기만 만든다.
 */
class ReplicaProductLockImpl implements ReplicaProductLock {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockProduct(Long productId) {
        entityManager.createNativeQuery("select pg_advisory_xact_lock(hashtextextended(:key, 0))")
            .setParameter("key", "kidly.community.replica.product." + productId)
            .getSingleResult();
    }
}
