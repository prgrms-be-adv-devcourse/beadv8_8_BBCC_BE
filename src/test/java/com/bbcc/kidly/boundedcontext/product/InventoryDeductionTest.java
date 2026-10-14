package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.InventoryDeductionFixture.NAVY_110_ID;
import static com.bbcc.kidly.boundedcontext.product.InventoryDeductionFixture.NAVY_120_ID;
import static com.bbcc.kidly.boundedcontext.product.InventoryDeductionFixture.ORDER_ID;
import static com.bbcc.kidly.boundedcontext.product.InventoryDeductionFixture.item;
import static com.bbcc.kidly.boundedcontext.product.InventoryDeductionFixture.onSaleOptions;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.bbcc.kidly.boundedcontext.product.InventoryDeductionFixture.Options;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryDeduction;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryDeductionRequest;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryDeductionRequest.OptionQuantity;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryDeductionStatus;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InventoryDeductionTest {

    private final Options options = onSaleOptions();

    @Test
    @DisplayName("INV-04 차감하면 주문상품마다 DEDUCTED 차감 기록을 만든다")
    void createDeductedRecordPerOrderItem() {
        InventoryDeductionRequest request = InventoryDeductionRequest.of(ORDER_ID, List.of(
            item(11L, options.navy110(), 2),
            item(12L, options.navy120(), 1)));

        assertThat(request.deductedRecords())
            .extracting(InventoryDeduction::getOrderId, InventoryDeduction::getOrderItemId,
                InventoryDeduction::getProductOptionId, InventoryDeduction::getQty, InventoryDeduction::getStatus)
            .containsExactly(
                tuple(ORDER_ID, 11L, NAVY_110_ID, 2, InventoryDeductionStatus.DEDUCTED),
                tuple(ORDER_ID, 12L, NAVY_120_ID, 1, InventoryDeductionStatus.DEDUCTED));
    }

    @Test
    @DisplayName("INV-11 거절한 주문과 취소가 먼저 온 주문(INV-09)은 주문상품마다 FAILED 차감 기록을 만든다")
    void createFailedRecordPerOrderItem() {
        InventoryDeductionRequest request = InventoryDeductionRequest.of(ORDER_ID, List.of(
            item(11L, options.navy110(), 2),
            item(12L, options.navy120(), 1)));

        assertThat(request.failedRecords())
            .extracting(InventoryDeduction::getOrderId, InventoryDeduction::getOrderItemId,
                InventoryDeduction::getProductOptionId, InventoryDeduction::getQty, InventoryDeduction::getStatus)
            .containsExactly(
                tuple(ORDER_ID, 11L, NAVY_110_ID, 2, InventoryDeductionStatus.FAILED),
                tuple(ORDER_ID, 12L, NAVY_120_ID, 1, InventoryDeductionStatus.FAILED));
    }

    @Test
    @DisplayName("INV-12 같은 옵션의 수량을 더해 옵션 ID 오름차순으로 돌려준다")
    void sumQuantitiesByOptionInAscendingOptionId() {
        InventoryDeductionRequest request = InventoryDeductionRequest.of(ORDER_ID, List.of(
            item(11L, options.navy110(), 2),
            item(12L, options.navy120(), 1),
            item(13L, options.navy110(), 1)));

        assertThat(request.optionQuantities())
            .extracting(OptionQuantity::optionId, OptionQuantity::qty)
            .containsExactly(tuple(NAVY_120_ID, 1), tuple(NAVY_110_ID, 3));
    }

    @Test
    @DisplayName("INV-15 같은 주문상품 ID가 두 번 들어 있으면 차감 요청을 만들지 않는다")
    void rejectDuplicateOrderItemId() {
        assertThatThrownBy(() -> InventoryDeductionRequest.of(ORDER_ID, List.of(
            item(11L, options.navy110(), 2),
            item(11L, options.navy120(), 1))))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
