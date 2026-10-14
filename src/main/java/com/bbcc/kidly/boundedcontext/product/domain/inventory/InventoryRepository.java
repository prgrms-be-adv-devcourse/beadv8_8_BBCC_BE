package com.bbcc.kidly.boundedcontext.product.domain.inventory;

public interface InventoryRepository {

    // 재고 행과 재고 조정 기록을 함께 저장한다
    void saveOpening(InventoryOpening opening);
}
