package com.bbcc.kidly.boundedcontext.product.domain.inventory;

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 주문 취소됨 하나로 되돌릴 재고 복구 대상이다. 주문상품의 차감 기록 중 되돌릴 기록만 골라 준다.
 */
public final class InventoryRestoration {

    private final List<InventoryDeduction> records;

    private InventoryRestoration(List<InventoryDeduction> records) {
        this.records = records;
    }

    public static InventoryRestoration of(List<InventoryDeduction> records) {
        // 받은 뒤 밖에서 목록을 바꿔도 그대로 남도록 복사한다. null 기록도 여기서 거절된다
        return new InventoryRestoration(List.copyOf(requireNonNull(records, "records는 필수입니다")));
    }

    // 차감한 기록(DEDUCTED)만 되돌린다. 거절한 기록(FAILED)은 줄인 재고가 없고, 복구한 기록(RESTORED)은 이미 되돌렸다 (INV-04)
    // 재고 행을 옵션 ID 오름차순으로 바꿔야 두 작업이 서로의 행을 기다리는 교착이 생기지 않는다 (INV-12)
    public List<InventoryDeduction> deductedRecords() {
        List<InventoryDeduction> targets = new ArrayList<>();
        for (InventoryDeduction record : records) {
            if (record.getStatus() == InventoryDeductionStatus.DEDUCTED) {
                targets.add(record);
            }
        }
        targets.sort(Comparator.comparing(InventoryDeduction::getProductOptionId));
        return targets;
    }
}
