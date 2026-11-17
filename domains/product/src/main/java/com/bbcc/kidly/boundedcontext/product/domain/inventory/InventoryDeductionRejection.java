package com.bbcc.kidly.boundedcontext.product.domain.inventory;

import com.bbcc.kidly.shared.product.domain.StockDeductionFailureCode;
import java.util.List;

public record InventoryDeductionRejection(List<Failure> failures) {

    public InventoryDeductionRejection {
        // 받은 뒤 밖에서 목록을 바꿔도 그대로 남도록 복사한다. null 줄도 여기서 거절된다
        failures = List.copyOf(failures);
        // 거절한 주문상품이 없으면 대표 사유를 정할 수 없다
        if (failures.isEmpty()) {
            throw new IllegalArgumentException("거절한 주문상품이 하나 이상 있어야 합니다");
        }
    }

    public StockDeductionFailureCode reason() {
        return failures.get(0).reason();
    }

    public record Failure(long orderItemId, StockDeductionFailureCode reason) {
    }
}
