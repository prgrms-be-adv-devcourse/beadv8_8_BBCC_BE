package com.bbcc.kidly.boundedcontext.order.out.persistence;

import com.bbcc.kidly.boundedcontext.order.app.port.SingleServerJobRunner;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 이름 붙은 작업을 한 번에 한 서버에서만 실행한다(#564): PostgreSQL 트랜잭션 advisory lock(kidly.order.{작업}).
 * <ul>
 *   <li>새 트랜잭션에서 pg_try_advisory_xact_lock으로 잡는다. 못 잡으면(다른 서버가 도는 중) 기다리지 않고 건너뛴다(DEBUG 한 줄)</li>
 *   <li>잡으면 그 트랜잭션을 멈춘 채(연결 · 잠금은 유지) 작업을 트랜잭션 밖에서 실행한다. 작업 안의 처리(대상 주문 하나하나)는
 *       지금처럼 각자 트랜잭션에서 커밋한다 — 잠금 트랜잭션에 합류하면 한 건 실패가 전체를 되돌린다</li>
 *   <li>작업이 끝나면(예외 포함) 잠금 트랜잭션이 끝나 풀린다. 서버가 죽어 연결이 끊겨도 DB가 푼다</li>
 * </ul>
 * 실행 중에는 잠금을 쥔 연결 하나를 더 쓴다(스케줄러 스레드가 하나라 서버당 많아야 하나).
 */
@Slf4j
@Component
class AdvisoryLockJobRunner implements SingleServerJobRunner {

    private static final String KEY_PREFIX = "kidly.order.";

    @PersistenceContext
    private EntityManager entityManager;

    private final TransactionTemplate lockTransaction;
    private final TransactionTemplate withoutTransaction;

    AdvisoryLockJobRunner(PlatformTransactionManager transactionManager) {
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
