package com.bbcc.kidly.boundedcontext.payment.app;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 트랜잭션 안에서 부르면 커밋된 뒤에 실행하고, 롤백되면 실행하지 않는다. 트랜잭션 밖이면 바로 실행한다.
 * 바깥 트랜잭션의 커밋 뒤 콜백 안에서 (새 트랜잭션 없이) 부르면 등록한 콜백이 실행되지 않는다.
 */
final class AfterCommit {

    private AfterCommit() {
    }

    static void run(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
            return;
        }
        action.run();
    }
}
