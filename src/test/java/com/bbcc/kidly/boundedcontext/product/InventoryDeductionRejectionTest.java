package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.InventoryDeductionFixture.ORDER_ID;
import static com.bbcc.kidly.boundedcontext.product.InventoryDeductionFixture.beforeSaleOptions;
import static com.bbcc.kidly.boundedcontext.product.InventoryDeductionFixture.item;
import static com.bbcc.kidly.boundedcontext.product.InventoryDeductionFixture.onSaleOptions;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryDeductionRejection;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryDeductionRequest;
import com.bbcc.kidly.shared.product.domain.StockDeductionFailureCode;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 거절한 차감 요청이 재고 부족함에 담을 주문상품별 거절 사유를 만드는지 확인한다.
 */
class InventoryDeductionRejectionTest {

    @Test
    @DisplayName("INV-13 상품이 판매중이 아니면 그 상품의 옵션을 담은 주문상품만 PRODUCT_NOT_ON_SALE로 거절한다")
    void productNotOnSale() {
        InventoryDeductionRequest request = InventoryDeductionRequest.of(ORDER_ID, List.of(
            item(11L, onSaleOptions().navy110(), 2),
            item(12L, beforeSaleOptions().navy120(), 1)));

        InventoryDeductionRejection rejection = request.notOnSaleRejection();

        assertThat(rejection.failures())
            .extracting(InventoryDeductionRejection.Failure::orderItemId, InventoryDeductionRejection.Failure::reason)
            .containsExactly(tuple(12L, StockDeductionFailureCode.PRODUCT_NOT_ON_SALE));
    }
}
