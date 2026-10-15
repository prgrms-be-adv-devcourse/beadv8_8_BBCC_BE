package com.bbcc.kidly.boundedcontext.product.out.product;

import com.bbcc.kidly.boundedcontext.product.domain.product.Product;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
class JpaProductRepositoryImpl {

    private final EntityManager entityManager;

    // 호출한 쪽의 트랜잭션이 커밋될 때 상품 행의 version을 1 올린다
    public void incrementVersion(Product product) {
        entityManager.lock(product, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
    }
}
