package com.bbcc.kidly.boundedcontext.favorite.app;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 넘긴 일을 따로 연 트랜잭션에서 한다(REQUIRES_NEW). 처음 보는 찜 저장이 유일 제약에 막혀도(동시 찜) 이 트랜잭션만 되돌리고
 * 예외를 던지므로 부르는 쪽은 "이미 찜"으로 끝낼 수 있다. 같은 클래스 안의 호출은 트랜잭션이 걸리지 않아 서비스와 나눴다.
 */
@Component
public class FavoriteSeparateTransaction {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void run(Runnable work) {
        work.run();
    }
}
