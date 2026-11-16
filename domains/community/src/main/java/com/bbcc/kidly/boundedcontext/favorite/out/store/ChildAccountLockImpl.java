package com.bbcc.kidly.boundedcontext.favorite.out.store;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 자녀 계정 ID별 트랜잭션 advisory lock(키 = hashtextextended('kidly.community.favorite.child-account.' + 계정 ID, 0)).
 * 아직 없는 행도 잠글 수 있어(행 잠금 FOR SHARE는 없는 행을 잠그지 않는다) 삭제 기록 전후를 직렬화한다. 커밋 · 롤백 때 DB가 푼다.
 * 이름 접두어로 다른 커뮤니티 잠금(복사본 상품 · 주기 실행)과 키를 나누고, 해시 충돌은 불필요한 대기만 만든다.
 */
class ChildAccountLockImpl implements ChildAccountLock {

    static final String KEY_PREFIX = "kidly.community.favorite.child-account.";

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockChildAccountExclusive(Long accountId) {
        lock("select pg_advisory_xact_lock(hashtextextended(:key, 0))", accountId);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockChildAccountShared(Long accountId) {
        lock("select pg_advisory_xact_lock_shared(hashtextextended(:key, 0))", accountId);
    }

    private void lock(String sql, Long accountId) {
        entityManager.createNativeQuery(sql).setParameter("key", KEY_PREFIX + accountId).getSingleResult();
    }
}
