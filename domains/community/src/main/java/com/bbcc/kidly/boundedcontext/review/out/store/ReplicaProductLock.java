package com.bbcc.kidly.boundedcontext.review.out.store;

/**
 * 같은 상품의 복사본 반영을 최초 조회 전부터 트랜잭션 종료까지 직렬화하는 저장소 조각.
 */
public interface ReplicaProductLock {

    void lockProduct(Long productId);
}
