package com.bbcc.kidly.boundedcontext.favorite.out.store;

/**
 * 자녀 계정별 트랜잭션 advisory lock 저장소 조각. 그 계정의 삭제 처리(배타)와 찜 추가 저장(공유)을 직렬화한다.
 */
public interface ChildAccountLock {

    void lockChildAccountExclusive(Long accountId);

    void lockChildAccountShared(Long accountId);
}
