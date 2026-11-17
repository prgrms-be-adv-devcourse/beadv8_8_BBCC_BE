package com.bbcc.kidly.boundedcontext.product.domain.inventory;

import com.bbcc.kidly.boundedcontext.product.domain.product.ProductOption;
import java.util.List;

public record InventoryOpening(Inventory inventory, List<InventoryAdjustmentRecord> adjustmentRecords) {

    public static InventoryOpening open(ProductOption option, int initialQty, long sellerId) {
        var inventory = Inventory.create(option, initialQty);
        if (initialQty == 0) {
            return new InventoryOpening(inventory, List.of());
        }
        InventoryAdjustmentRecord initialRecord =
                InventoryAdjustmentRecord.create(option, initialQty, InventoryAdjustmentReason.INITIAL, sellerId);
        return new InventoryOpening(inventory, List.of(initialRecord));
    }
}
