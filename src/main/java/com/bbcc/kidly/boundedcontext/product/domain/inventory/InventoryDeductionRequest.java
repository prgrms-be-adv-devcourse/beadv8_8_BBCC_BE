package com.bbcc.kidly.boundedcontext.product.domain.inventory;

import static java.util.Objects.requireNonNull;

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

/**
 * 주문 생성됨 하나로 만든 재고 차감 요청이다. 차감 기록은 이 요청만 만든다.
 * 주문 취소됨이 주문 생성됨보다 먼저 오면, 주문 취소됨의 주문상품으로 같은 요청을 만들어 FAILED 기록을 남긴다 (INV-09).
 */
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
        List<Item> copied = List.copyOf(requireNonNull(items, "items는 필수입니다"));
        if (copied.isEmpty()) {
            throw new IllegalArgumentException("차감할 주문상품은 비어 있을 수 없습니다");
        }
        // 한 주문 생성됨 안에서 주문상품 ID는 겹치지 않는다. 겹치면 주문 코드의 오류라 요청을 만들지 않는다 (INV-15)
        Set<Long> orderItemIds = new HashSet<>();
        for (Item item : copied) {
            requireNonNull(item.option(), "option은 필수입니다");
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

    // 옵션 ID 오름차순으로 정렬된 옵션별 수량 합계를 만든다
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

    // 재고 차감됨 이벤트에 담을 주문 ID를 돌려준다
    public long orderId() {
        return orderId;
    }

    // 같은 주문 생성됨을 다시 받았는지를 주문상품의 기존 차감 기록으로 판단한다 (INV-10)
    // existingRecords는 이 요청의 주문상품 ID로 조회한 기록이어야 한다. 요청에 없는 주문상품의 기록은 보지 않는다
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

    // 주문상품의 옵션을 모두 살 수 있어야 차감한다. 판단은 옵션의 판매 가능 판단 메서드(SALE-04)에 맡긴다 (INV-13)
    public boolean isPurchasable() {
        for (Item item : items) {
            if (!item.option().isPurchasable()) {
                return false;
            }
        }
        return true;
    }

    // 판매중이 아니어서 거절한 요청의 사유를 만든다. 살 수 없는 옵션을 담은 주문상품만 요청한 순서대로 담는다 (INV-13)
    public InventoryDeductionRejection notOnSaleRejection() {
        List<InventoryDeductionRejection.Failure> failures = new ArrayList<>();
        for (Item item : items) {
            Optional<StockDeductionFailureCode> reason = item.option().notPurchasableReason();
            if (reason.isPresent()) {
                failures.add(new InventoryDeductionRejection.Failure(item.orderItemId(), reason.get()));
            }
        }
        return new InventoryDeductionRejection(failures);
    }

    // 주문상품마다 DEDUCTED 차감 기록을 하나씩, 요청한 순서대로 만든다 (INV-04)
    public List<InventoryDeduction> deductedRecords() {
        List<InventoryDeduction> records = new ArrayList<>();
        for (Item item : items) {
            records.add(InventoryDeduction.deducted(orderId, item));
        }
        return records;
    }

    // 재고가 부족하거나 판매중이 아니어서 거절한 주문(INV-11)과 취소가 먼저 온 주문(INV-09)은 주문상품마다 FAILED 차감 기록을 남긴다
    public List<InventoryDeduction> failedRecords() {
        List<InventoryDeduction> records = new ArrayList<>();
        for (Item item : items) {
            records.add(InventoryDeduction.failed(orderId, item));
        }
        return records;
    }

    // 같은 옵션의 수량을 더해 옵션 ID 오름차순으로 돌려준다. 재고 행을 이 순서로 줄여야 두 차감이 서로의 행을 기다리는 교착 상태가 생기지 않는다 (INV-12)
    public List<OptionQuantity> optionQuantities() {
        List<OptionQuantity> quantities = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : qtyByOptionId.entrySet()) {
            quantities.add(new OptionQuantity(entry.getKey(), entry.getValue()));
        }
        return quantities;
    }

    // 이번 차감에서 줄일 옵션 ID를 오름차순으로 돌려준다. 같은 옵션이 여러 주문상품에 나와도 한 번만 담는다
    public List<Long> optionIds() {
        return List.copyOf(qtyByOptionId.keySet());
    }

    // 이번 차감으로 판매 가능 수량이 0 이하가 된 옵션 ID를 오름차순으로 돌려준다. SALE-08은 이 옵션을 품절로 보고 상품 정보 변경됨으로 알린다
    // availableQtyByOptionId는 차감한 뒤의 수량이다. 수량이 없는 옵션은 재고 행이 없는 것이라 0으로 본다
    public List<Long> soldOutOptionIds(Map<Long, Integer> availableQtyByOptionId) {
        List<Long> soldOutOptionIds = new ArrayList<>();
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
