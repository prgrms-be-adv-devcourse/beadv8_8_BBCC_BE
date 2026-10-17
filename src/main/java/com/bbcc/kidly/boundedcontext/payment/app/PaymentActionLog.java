package com.bbcc.kidly.boundedcontext.payment.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 결제 행동 로그. 예치금 · 충전 · 결제 상태가 바뀐 일을 action.payment 로거에 INFO 한 줄로 남긴다.
 * 한 줄은 {@code 주문 결제 완료 action=ORDER_PAID memberId=1 ...}처럼 한글 설명 · action 뒤에 key=value를 잇는다.
 * 트랜잭션 안에서 부르면 커밋된 뒤에 남긴다. 롤백된 일은 남기지 않는다.
 * 바깥 트랜잭션의 커밋 뒤 콜백 안에서 (새 트랜잭션 없이) 부르면 등록한 콜백이 실행되지 않아 남지 않는다.
 * 지금 부르는 곳은 모두 자기 트랜잭션(@Transactional · REQUIRES_NEW · @DomainEventListener) 안이다.
 */
public final class PaymentActionLog {

    public static final String LOGGER_NAME = "action.payment";
    private static final Logger LOG = LoggerFactory.getLogger(LOGGER_NAME);

    private PaymentActionLog() {
    }

    /**
     * @param fields {@code memberId={} amount={}}처럼 key={}를 공백으로 이은 형식
     * @param args   fields의 {}에 들어갈 값
     */
    public static void record(PaymentAction action, String fields, Object... args) {
        if (!LOG.isInfoEnabled()) {
            return;
        }
        String format = "{} action={} " + fields;
        Object[] formatArgs = new Object[args.length + 2];
        formatArgs[0] = action.description();
        formatArgs[1] = action;
        System.arraycopy(args, 0, formatArgs, 2, args.length);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    LOG.info(format, formatArgs);
                }
            });
            return;
        }
        LOG.info(format, formatArgs);
    }
}
