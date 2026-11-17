package com.bbcc.kidly.boundedcontext.product.domain.inventory;

import com.bbcc.kidly.boundedcontext.product.domain.product.ProductOption;
import com.bbcc.kidly.shared.product.domain.StockDeductionFailureCode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

public final class InventoryDeductionRequest {

    private final long orderId;
    private final List<Item> items;
    // 같은 옵션의 수량을 요청을 만들 때 더해 두고, int 범위를 넘으면 차감이 재고를 늘리므로 요청을 만들지 않는다 (INV-01)
    private final SortedMap<Long, Integer> qtyByOptionId;

    private InventoryDeductionRequest(long orderId, List<Item> items, SortedMap<Long, Integer> qtyByOptionId) {
        this.orderId = orderId;
        this.items = items;
        this.qtyByOptionId = qtyByOptionId;
    }

    public static InventoryDeductionRequest of(long orderId, List<Item> items) {
        // 받은 뒤 밖에서 목록을 바꿔도 검증한 내용이 그대로 남도록 복사한다. null 주문상품도 여기서 거절된다
        List<Item> copied = List.copyOf(items);
        if (copied.isEmpty()) {
            throw new IllegalArgumentException("차감할 주문상품은 비어 있을 수 없습니다");
        }
        // 한 주문 생성됨 안에서 주문상품 ID는 겹치지 않는다. 겹치면 주문 코드의 오류라 요청을 만들지 않는다 (INV-15)
        var orderItemIds = new HashSet<Long>();
        for (Item item : copied) {
            if (!orderItemIds.add(item.orderItemId())) {
                throw new IllegalArgumentException("주문상품 ID가 중복될 수 없습니다: " + item.orderItemId());
            }
            // 0 이하를 차감하면 재고가 늘거나 아무것도 줄지 않으므로 받지 않는다
            if (item.qty() <= 0) {
                throw new IllegalArgumentException("차감 수량은 0보다 커야 합니다");
            }
        }
        return new InventoryDeductionRequest(orderId, copied, sumQtyByOptionId(copied));
    }

    private static SortedMap<Long, Integer> sumQtyByOptionId(List<Item> items) {
        SortedMap<Long, Integer> sums = new TreeMap<>();
        for (Item item : items) {
            Long optionId = item.option().getId();
            try {
                sums.merge(optionId, item.qty(), Math::addExact);
            } catch (ArithmeticException e) {
                throw new IllegalArgumentException("같은 옵션의 차감 수량 합계가 너무 큽니다", e);
            }
        }
        return sums;
    }

    public long orderId() {
        return orderId;
    }

    public boolean isAlreadyHandled(List<InventoryDeduction> existingRecords) {
        if (existingRecords.isEmpty()) {
            return false;
        }
        Map<Long, InventoryDeduction> recordByOrderItemId = new HashMap<>();
        for (InventoryDeduction existing : existingRecords) {
            recordByOrderItemId.put(existing.getOrderItemId(), existing);
        }
        // 주문상품은 만든 뒤 바뀌지 않으므로, 같은 주문상품 ID에 다른 내용이 오면 주문 쪽 오류라 처리하지 않는다
        for (Item item : items) {
            if (!matches(recordByOrderItemId.get(item.orderItemId()), item)) {
                throw new IllegalArgumentException("주문상품 " + item.orderItemId() + "의 기존 차감 기록이 요청과 다릅니다");
            }
        }
        return true;
    }

    private boolean matches(InventoryDeduction existing, Item item) {
        if (existing == null) {
            return false;
        }
        return Objects.equals(existing.getProductOptionId(), item.option().getId())
                && existing.getQty() == item.qty();
    }

    public boolean isPurchasable() {
        return items.stream().allMatch(item -> item.option().isPurchasable());
    }

    public InventoryDeductionRejection notOnSaleRejection() {
        var failures = new ArrayList<InventoryDeductionRejection.Failure>();
        for (Item item : items) {
            Optional<StockDeductionFailureCode> reason = item.option().notPurchasableReason();
            if (reason.isPresent()) {
                failures.add(new InventoryDeductionRejection.Failure(item.orderItemId(), reason.get()));
            }
        }
        return new InventoryDeductionRejection(failures);
    }

    public InventoryDeductionRejection shortageRejection(Set<Long> shortOptionIds) {
        var failures = new ArrayList<InventoryDeductionRejection.Failure>();
        for (Item item : items) {
            if (shortOptionIds.contains(item.option().getId())) {
                failures.add(new InventoryDeductionRejection.Failure(
                    item.orderItemId(), StockDeductionFailureCode.OUT_OF_STOCK));
            }
        }
        return new InventoryDeductionRejection(failures);
    }

    public List<InventoryDeduction> deductedRecords() {
        var records = new ArrayList<InventoryDeduction>();
        for (Item item : items) {
            records.add(InventoryDeduction.deducted(orderId, item));
        }
        return records;
    }

    public List<InventoryDeduction> failedRecords() {
        var records = new ArrayList<InventoryDeduction>();
        for (Item item : items) {
            records.add(InventoryDeduction.failed(orderId, item));
        }
        return records;
    }

    public List<OptionQuantity> optionQuantities() {
        var quantities = new ArrayList<OptionQuantity>();
        for (Map.Entry<Long, Integer> entry : qtyByOptionId.entrySet()) {
            quantities.add(new OptionQuantity(entry.getKey(), entry.getValue()));
        }
        return quantities;
    }

    public List<Long> optionIds() {
        return List.copyOf(qtyByOptionId.keySet());
    }

    public List<Long> soldOutOptionIds(Map<Long, Integer> availableQtyByOptionId) {
        var soldOutOptionIds = new ArrayList<Long>();
        for (Long optionId : qtyByOptionId.keySet()) {
            if (availableQtyByOptionId.getOrDefault(optionId, 0) <= 0) {
                soldOutOptionIds.add(optionId);
            }
        }
        return soldOutOptionIds;
    }

    public record Item(long orderItemId, ProductOption option, int qty) {
    }

    public record OptionQuantity(long optionId, int qty) {
    }
}
