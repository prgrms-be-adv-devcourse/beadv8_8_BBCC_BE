package com.bbcc.kidly.boundedcontext.product.out.inventory;

import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryAdjustmentRecord;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryOpening;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
class JpaInventoryRepositoryImpl {

    private final EntityManager entityManager;

    public void saveOpening(InventoryOpening opening) {
        entityManager.persist(opening.inventory());
        for (InventoryAdjustmentRecord record : opening.adjustmentRecords()) {
            entityManager.persist(record);
        }
    }

    public void saveAdjustmentRecord(InventoryAdjustmentRecord record) {
        entityManager.persist(record);
    }
}
