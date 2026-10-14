package com.bbcc.kidly.boundedcontext.product.domain.inventory;

import com.bbcc.kidly.boundedcontext.product.domain.product.ProductOption;
import java.util.List;

/**
 * 옵션을 등록할 때 함께 만드는 재고 행과 초기 재고 기록이다. 등록 서비스가 돌려받은 것을 저장한다.
 */
public record InventoryOpening(Inventory inventory, List<InventoryAdjustmentRecord> adjustmentRecords) {

    // 판매 가능 수량은 초기 재고이고, 초기 재고 기록의 증감량이 이 수량과 같다 (INV-02)
    public static InventoryOpening open(ProductOption option, int initialQty, long sellerId) {
        Inventory inventory = Inventory.create(option, initialQty);
        // 증감량이 0인 기록은 남기지 않으므로, 초기 재고가 0이면 INITIAL 기록을 만들지 않는다 (INV-18, INV-19)
        if (initialQty == 0) {
            return new InventoryOpening(inventory, List.of());
        }
        InventoryAdjustmentRecord initialRecord =
                InventoryAdjustmentRecord.create(option, initialQty, InventoryAdjustmentReason.INITIAL, sellerId);
        return new InventoryOpening(inventory, List.of(initialRecord));
    }
}
