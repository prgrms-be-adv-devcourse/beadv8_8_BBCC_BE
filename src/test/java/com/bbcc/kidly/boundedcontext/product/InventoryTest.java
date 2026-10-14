package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductFixture.SELLER_ID;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.clothingOptions;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.navy110;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryAdjustmentRecord;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryOpening;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class InventoryTest {

    private final ProductOption option = navy110(clothingOptions());

    @ParameterizedTest(name = "초기 재고 {0}")
    @ValueSource(ints = {0, 1, 10})
    @DisplayName("INV-02 재고를 열면 판매 가능 수량이 초기 재고이고, 재고 조정 기록 증감량의 합과 같다")
    void availableQtyEqualsAdjustmentSum(int initialQty) {

        InventoryOpening opening = InventoryOpening.open(option, initialQty, SELLER_ID);

        int adjustmentSum = opening.records().stream().mapToInt(InventoryAdjustmentRecord::getDeltaQty).sum();
        assertThat(opening.inventory().getAvailableQty()).isEqualTo(initialQty);
        assertThat(adjustmentSum).isEqualTo(initialQty);
    }
}
