package com.bbcc.kidly.boundedcontext.delivery.out.lock;

import com.bbcc.kidly.boundedcontext.delivery.app.port.DeliveryJobRunner;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 이름 붙은 배송 작업을 한 번에 한 서버에서만 실행한다: PostgreSQL 트랜잭션 advisory lock(kidly.delivery.{작업}).
 * 주문 컨텍스트의 AdvisoryLockJobRunner와 같은 방식이다(컨텍스트끼리 out 구현을 나눠 쓰지 않아 따로 둔다).
 * 못 잡으면 기다리지 않고 건너뛰고, 잡으면 잠금 트랜잭션을 멈춘 채 작업을 트랜잭션 밖에서 실행한다(건마다 각자 커밋).
 */
@Slf4j
@Component
class DeliveryAdvisoryLockJobRunner implements DeliveryJobRunner {

    private static final String KEY_PREFIX = "kidly.delivery.";

    @PersistenceContext
    private EntityManager entityManager;

    private final TransactionTemplate lockTransaction;
    private final TransactionTemplate withoutTransaction;

    DeliveryAdvisoryLockJobRunner(PlatformTransactionManager transactionManager) {
        this.lockTransaction = new TransactionTemplate(transactionManager);
        this.lockTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.withoutTransaction = new TransactionTemplate(transactionManager);
        this.withoutTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
    }

    @Override
    public boolean run(String jobName, Runnable job) {
        return Boolean.TRUE.equals(lockTransaction.execute(status -> {
            if (!tryLock(jobName)) {
                log.debug("다른 서버가 작업 중이라 이번 주기는 건너뜁니다: job={}", jobName);
                return false;
            }
            withoutTransaction.executeWithoutResult(inner -> job.run());
            return true;
        }));
    }

    private boolean tryLock(String jobName) {
        Object locked = entityManager.createNativeQuery("select pg_try_advisory_xact_lock(hashtext(:key))")
            .setParameter("key", KEY_PREFIX + jobName)
            .getSingleResult();
        return Boolean.TRUE.equals(locked);
    }
}
