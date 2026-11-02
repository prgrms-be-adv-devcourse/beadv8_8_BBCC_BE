package com.bbcc.kidly.boundedcontext.review.app;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 넘긴 일을 따로 연 트랜잭션에서 한다(REQUIRES_NEW). 처음 보는 행 저장이 유일 제약에 막혀도(동시 수신) 이 트랜잭션만 되돌리고 예외를
 * 던지므로, 부르는 쪽 트랜잭션(Spring 이벤트 수신 · Kafka 처리)은 이어서 "이미 있음"을 처리하고 커밋할 수 있다.
 * 같은 클래스 안의 호출은 트랜잭션이 걸리지 않아 서비스와 나눴다.
 */
@Component
public class ReviewSeparateTransaction {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void run(Runnable work) {
        work.run();
    }
}
