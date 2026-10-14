package com.bbcc.kidly.boundedcontext.product.domain.inventory;

/**
 * 재고를 조정한 사유다.
 */
public enum InventoryAdjustmentReason {
    INITIAL,
    RESTOCK,
    DEFECTIVE,
    LOST,
    CORRECTION
}
