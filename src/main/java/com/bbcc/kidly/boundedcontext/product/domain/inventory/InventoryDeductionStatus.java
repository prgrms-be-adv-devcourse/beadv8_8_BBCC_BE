package com.bbcc.kidly.boundedcontext.product.domain.inventory;

/**
 * 재고 차감 기록의 상태다.
 */
public enum InventoryDeductionStatus {
    // 재고를 차감했다
    DEDUCTED,
    // 차감한 재고를 복구했다
    RESTORED,
    // 재고를 차감하지 않았다
    FAILED
}
