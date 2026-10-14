package com.bbcc.kidly.boundedcontext.product.domain.inventory;

import com.bbcc.kidly.boundedcontext.product.domain.product.ProductOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

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
        // 한 주문 생성됨 안에서 주문상품 ID는 겹치지 않는다. 겹치면 주문 코드의 오류라 요청을 만들지 않는다 (INV-15)
        Set<Long> orderItemIds = new HashSet<>();
        for (Item item : items) {
            if (!orderItemIds.add(item.orderItemId())) {
                throw new IllegalArgumentException("주문상품 ID가 중복될 수 없습니다: " + item.orderItemId());
            }
        }
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

    // 재고가 부족하거나 판매중이 아니어서 거절한 주문(INV-11)과 취소가 먼저 온 주문(INV-09)은 주문상품마다 FAILED 차감 기록을 남긴다
    public List<InventoryDeduction> failedRecords() {
        List<InventoryDeduction> records = new ArrayList<>();
        for (Item item : items) {
            records.add(InventoryDeduction.failed(orderId, item.orderItemId(), item.option(), item.qty()));
        }
        return records;
    }

    // 같은 옵션의 수량을 더해 옵션 ID 오름차순으로 돌려준다. 재고 행을 이 순서로 줄여야 두 차감이 서로의 행을 기다리는 교착 상태가 생기지 않는다 (INV-12)
    public List<OptionQuantity> optionQuantities() {
        Map<Long, Integer> qtyByOptionId = new TreeMap<>();
        for (Item item : items) {
            qtyByOptionId.merge(item.option().getId(), item.qty(), Integer::sum);
        }
        List<OptionQuantity> quantities = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : qtyByOptionId.entrySet()) {
            quantities.add(new OptionQuantity(entry.getKey(), entry.getValue()));
        }
        return quantities;
    }

    public record Item(long orderItemId, ProductOption option, int qty) {
    }

    public record OptionQuantity(long optionId, int qty) {
    }
}
