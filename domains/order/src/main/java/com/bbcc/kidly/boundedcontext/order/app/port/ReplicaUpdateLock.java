package com.bbcc.kidly.boundedcontext.order.app.port;

/**
 * 같은 대상의 복사본 반영을 조회 전부터 트랜잭션 종료까지 직렬화한다. 아직 행이 없는 최초 생성도 보호한다.
 */
public interface ReplicaUpdateLock {

    void acquire(Kind kind, Long targetId);

    enum Kind {
        PRODUCT, CHILD, SELLER, CHILD_ACCOUNT
    }
}
