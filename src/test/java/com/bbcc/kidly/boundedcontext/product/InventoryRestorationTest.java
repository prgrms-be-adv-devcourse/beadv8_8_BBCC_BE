package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.InventoryDeductionFixture.NAVY_110_ID;
import static com.bbcc.kidly.boundedcontext.product.InventoryDeductionFixture.NAVY_120_ID;
import static com.bbcc.kidly.boundedcontext.product.InventoryDeductionFixture.ORDER_ID;
import static com.bbcc.kidly.boundedcontext.product.InventoryDeductionFixture.item;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.bbcc.kidly.boundedcontext.product.InventoryDeductionFixture.Options;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryDeduction;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryDeductionRequest;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryDeductionStatus;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryRestoration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class InventoryRestorationTest {

    @Test
    @DisplayName("INV-12 주문의 DEDUCTED 차감 기록을 옵션 ID 오름차순으로 고른다")
    void pickDeductedRecordsInOptionIdOrder() {
        Options options = InventoryDeductionFixture.onSaleOptions();
        // 110(옵션 ID 7)을 먼저 주문해서, 기록도 옵션 ID가 큰 것부터 만들어진다
        List<InventoryDeduction> records = InventoryDeductionRequest.of(ORDER_ID, List.of(
            item(11L, options.navy110(), 2),
            item(12L, options.navy120(), 1))).deductedRecords();

        List<InventoryDeduction> targets = InventoryRestoration.of(records).deductedRecords();

        assertThat(targets)
            .extracting(InventoryDeduction::getOrderItemId, InventoryDeduction::getProductOptionId)
            .containsExactly(
                tuple(12L, NAVY_120_ID),
                tuple(11L, NAVY_110_ID));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = InventoryDeductionStatus.class, names = {"FAILED", "RESTORED"})
    @DisplayName("INV-04 FAILED와 RESTORED 차감 기록은 되돌리지 않는다")
    void skipRecordsNotDeducted(InventoryDeductionStatus status) {
        Options options = InventoryDeductionFixture.onSaleOptions();
        List<InventoryDeduction> records = InventoryDeductionFixture.withStatus(
            InventoryDeductionRequest.of(ORDER_ID, List.of(
                item(11L, options.navy110(), 2),
                item(12L, options.navy120(), 1))).deductedRecords(),
            status);

        assertThat(InventoryRestoration.of(records).deductedRecords()).isEmpty();
    }
}
