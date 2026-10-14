package com.bbcc.kidly.boundedcontext.product.domain.inventory;

import com.bbcc.kidly.boundedcontext.product.domain.product.ProductOption;
import java.util.ArrayList;
import java.util.List;

/**
 * 주문 생성됨 하나로 만든 재고 차감 요청이다. 차감 기록은 이 요청만 만든다.
 */
public final class InventoryDeductionRequest {

    private final long orderId;
    private final List<Item> items;

    private InventoryDeductionRequest(long orderId, List<Item> items) {
        this.orderId = orderId;
        this.items = items;
    }

    public static InventoryDeductionRequest of(long orderId, List<Item> items) {
        return new InventoryDeductionRequest(orderId, items);
    }

    // 주문상품마다 DEDUCTED 차감 기록을 하나씩, 요청한 순서대로 만든다 (INV-04)
    public List<InventoryDeduction> deductedRecords() {
        List<InventoryDeduction> records = new ArrayList<>();
        for (Item item : items) {
            records.add(InventoryDeduction.deducted(orderId, item.orderItemId(), item.option(), item.qty()));
        }
        return records;
    }

    public record Item(long orderItemId, ProductOption option, int qty) {
    }
}
