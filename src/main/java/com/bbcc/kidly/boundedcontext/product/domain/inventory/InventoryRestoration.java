package com.bbcc.kidly.boundedcontext.product.domain.inventory;

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 주문 취소됨 하나로 되돌릴 재고 복구 대상이다. 주문상품의 차감 기록으로 취소가 먼저 온 주문인지, 거절한 주문인지,
 * 되돌릴 기록이 무엇인지를 판단한다.
 */
public final class InventoryRestoration {

    private final List<InventoryDeduction> records;

    private InventoryRestoration(List<InventoryDeduction> records) {
        this.records = records;
    }

    public static InventoryRestoration of(long orderId, List<InventoryDeduction> records) {
        // 받은 뒤 밖에서 목록을 바꿔도 그대로 남도록 복사한다. null 기록도 여기서 거절된다
        List<InventoryDeduction> copied = List.copyOf(requireNonNull(records, "records는 필수입니다"));
        for (InventoryDeduction record : copied) {
            // 주문상품은 한 주문에만 속하므로 다른 주문의 기록이 나오면 주문 쪽 오류다
            if (record.getOrderId() != orderId) {
                throw new IllegalArgumentException("주문상품 " + record.getOrderItemId()
                    + "의 차감 기록이 다른 주문 " + record.getOrderId() + "의 것입니다");
            }
        }
        return new InventoryRestoration(copied);
    }

    // 주문 취소됨이 주문 생성됨보다 먼저 온 주문이라 주문상품마다 FAILED를 남겨야 한다 (INV-09)
    public boolean isCancelledBeforeCreated() {
        return records.isEmpty();
    }

    // 재고 부족이나 판매중지로 거절해 줄인 재고가 없는 주문이다
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

    // 차감한 기록(DEDUCTED)만 되돌린다. 거절한 기록(FAILED)은 줄인 재고가 없고, 복구한 기록(RESTORED)은 이미 되돌렸다 (INV-04)
    // 재고 행을 옵션 ID 오름차순으로 바꿔야 두 작업이 서로의 행을 기다리는 교착이 생기지 않는다 (INV-12)
    // 같은 옵션 안의 순서도 주문상품 ID 오름차순으로 정해야, 같은 주문을 동시에 되돌릴 때 기록 행을 서로 다른 순서로 잠그지 않는다
    public List<InventoryDeduction> recordsToRestore() {
        List<InventoryDeduction> targets = new ArrayList<>();
        for (InventoryDeduction record : records) {
            if (record.getStatus() == InventoryDeductionStatus.DEDUCTED) {
                targets.add(record);
            }
        }
        targets.sort(Comparator.comparing(InventoryDeduction::getProductOptionId)
            .thenComparing(InventoryDeduction::getOrderItemId));
        return targets;
    }
}
