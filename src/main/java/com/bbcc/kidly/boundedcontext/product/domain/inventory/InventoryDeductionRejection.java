package com.bbcc.kidly.boundedcontext.product.domain.inventory;

import static java.util.Objects.requireNonNull;

import com.bbcc.kidly.shared.product.domain.StockDeductionFailureCode;
import java.util.List;

/**
 * 거절한 차감 요청이 재고 부족함 이벤트에 담을 주문상품별 거절 사유다.
 */
public record InventoryDeductionRejection(List<Failure> failures) {

    public InventoryDeductionRejection {
        // 받은 뒤 밖에서 목록을 바꿔도 그대로 남도록 복사한다. null 줄도 여기서 거절된다
        failures = List.copyOf(requireNonNull(failures, "failures는 필수입니다"));
        // 거절한 주문상품이 없으면 대표 사유를 정할 수 없다
        if (failures.isEmpty()) {
            throw new IllegalArgumentException("거절한 주문상품이 하나 이상 있어야 합니다");
        }
    }

    // 대표 사유는 첫 줄의 사유다. 주문은 주문상품별 사유를 failures에서 읽고, 이 값은 로그에만 남긴다
    public StockDeductionFailureCode reason() {
        return failures.get(0).reason();
    }

    public record Failure(long orderItemId, StockDeductionFailureCode reason) {

        public Failure {
            requireNonNull(reason, "reason은 필수입니다");
        }
    }
}
