package com.bbcc.kidly.boundedcontext.product.domain.inventory;

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

    public boolean allows(int deltaQty) {
        return deltaQty > 0 ? canIncrease : deltaQty < 0 && canDecrease;
    }
}
