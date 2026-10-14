package com.bbcc.kidly.boundedcontext.product.out.inventory;

import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryAdjustmentRecord;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryOpening;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
class JpaInventoryRepositoryImpl {

    private final EntityManager entityManager;

    // 재고 행과 초기 재고 기록을 호출한 쪽의 트랜잭션 안에서 함께 저장한다
    public void saveOpening(InventoryOpening opening) {
        entityManager.persist(opening.inventory());
        for (InventoryAdjustmentRecord record : opening.adjustmentRecords()) {
            entityManager.persist(record);
        }
    }
}
