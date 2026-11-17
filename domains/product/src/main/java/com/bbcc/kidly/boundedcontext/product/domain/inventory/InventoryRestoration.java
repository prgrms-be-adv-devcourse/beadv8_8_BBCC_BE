package com.bbcc.kidly.boundedcontext.product.domain.inventory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

public final class InventoryRestoration {

    private final List<InventoryDeduction> records;

    private InventoryRestoration(List<InventoryDeduction> records) {
        this.records = records;
    }

    public static InventoryRestoration of(long orderId, List<InventoryDeduction> records) {
        // 받은 뒤 밖에서 목록을 바꿔도 그대로 남도록 복사한다. null 기록도 여기서 거절된다
        List<InventoryDeduction> copied = List.copyOf(records);
        for (InventoryDeduction record : copied) {
            // 주문상품은 한 주문에만 속하므로 다른 주문의 기록이 나오면 주문 쪽 오류다
            if (record.getOrderId() != orderId) {
                throw new IllegalArgumentException("주문상품 " + record.getOrderItemId()
                    + "의 차감 기록이 다른 주문 " + record.getOrderId() + "의 것입니다");
            }
        }
        return new InventoryRestoration(copied);
    }

    public boolean isCancelledBeforeCreated() {
        return records.isEmpty();
    }

    public List<Long> orderItemIdsWithoutRecord(Collection<Long> requestedOrderItemIds) {
        var recorded = new HashSet<Long>();
        for (InventoryDeduction record : records) {
            recorded.add(record.getOrderItemId());
        }
        var missing = new ArrayList<Long>();
        for (Long orderItemId : new TreeSet<>(requestedOrderItemIds)) {
            if (!recorded.contains(orderItemId)) {
                missing.add(orderItemId);
            }
        }
        return missing;
    }

    public boolean isRejected() {
        if (records.isEmpty()) {
            return false;
        }
        for (InventoryDeduction record : records) {
            if (record.getStatus() != InventoryDeductionStatus.FAILED) {
                return false;
            }
        }
        return true;
    }

    public List<InventoryDeduction> recordsToRestore() {
        var targets = new ArrayList<InventoryDeduction>();
        for (InventoryDeduction record : records) {
            if (record.getStatus() == InventoryDeductionStatus.DEDUCTED) {
                targets.add(record);
            }
        }
        targets.sort(Comparator.comparing(InventoryDeduction::getProductOptionId)
            .thenComparing(InventoryDeduction::getOrderItemId));
        return targets;
    }

    public static List<Long> soldOutReleasedOptionIds(
            Map<Long, Integer> restoredQtyByOptionId, Map<Long, Integer> availableQtyByOptionId) {
        var optionIds = new ArrayList<Long>();
        for (Map.Entry<Long, Integer> entry : restoredQtyByOptionId.entrySet()) {
            Integer availableQtyAfter = availableQtyByOptionId.get(entry.getKey());
            if (availableQtyAfter == null) {
                throw new IllegalArgumentException("옵션 " + entry.getKey() + "의 복구 뒤 판매 가능 수량이 없습니다");
            }
            int availableQtyBefore = availableQtyAfter - entry.getValue();
            if (availableQtyBefore <= 0) {
                optionIds.add(entry.getKey());
            }
        }
        optionIds.sort(Comparator.naturalOrder());
        return optionIds;
    }
}
