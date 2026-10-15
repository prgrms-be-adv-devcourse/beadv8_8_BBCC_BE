package com.bbcc.kidly.boundedcontext.product.out.product;

import com.bbcc.kidly.boundedcontext.product.domain.product.Product;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
class JpaProductRepositoryImpl {

    private final EntityManager entityManager;

    // 부르는 즉시 version 조건을 건 UPDATE로 상품 행의 version을 1 올리고 엔티티의 version도 맞춘다.
    // 커밋 직전으로 미루는 OPTIMISTIC_FORCE_INCREMENT는 응답에 담을 version이 커밋된 값보다 1 낮고,
    // 그 사이 영속성 컨텍스트가 비워지면 증가가 빠지므로 쓰지 않는다
    public void incrementVersion(Product product) {
        entityManager.lock(product, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
    }
}
