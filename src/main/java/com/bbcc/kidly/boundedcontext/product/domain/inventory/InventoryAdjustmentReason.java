package com.bbcc.kidly.boundedcontext.product.domain.inventory;

/**
 * 재고를 조정한 사유다. 사유마다 증감량의 부호가 정해져 있다.
 * 입고는 재고를 늘리고, 불량과 분실은 줄이고, 정정은 늘리거나 줄일 수 있다 (INV-18).
 */
public enum InventoryAdjustmentReason {
    INITIAL(true, false),
    RESTOCK(true, false),
    DEFECTIVE(false, true),
    LOST(false, true),
    CORRECTION(true, true);

    private final boolean canIncrease;
    private final boolean canDecrease;

    InventoryAdjustmentReason(boolean canIncrease, boolean canDecrease) {
        this.canIncrease = canIncrease;
        this.canDecrease = canDecrease;
    }

    // 증감량의 부호가 이 사유에서 허용되는지 알려 준다
    public boolean allows(int deltaQty) {
        if (deltaQty > 0) {
            return canIncrease;
        }
        return canDecrease;
    }
}
