package com.bbcc.kidly.boundedcontext.order.out.lock;

import com.bbcc.kidly.boundedcontext.order.app.port.ReplicaUpdateLock;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 복사본 종류 · 대상 ID별 트랜잭션 advisory lock. 상품은 옵션이 아닌 상품 ID로 잡아 빠진 옵션 처리까지 함께 보호한다.
 * 행이 없어도 잠글 수 있고, 커밋 · 롤백 · 연결 종료 때 DB가 해제한다. 해시 충돌은 불필요한 대기만 만든다.
 */
@Component
class ReplicaAdvisoryLock implements ReplicaUpdateLock {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void acquire(Kind kind, Long targetId) {
        entityManager.createNativeQuery("select pg_advisory_xact_lock(hashtextextended(:key, 0))")
            .setParameter("key", "kidly.order.replica." + kind.name() + "." + targetId)
            .getSingleResult();
    }
}
