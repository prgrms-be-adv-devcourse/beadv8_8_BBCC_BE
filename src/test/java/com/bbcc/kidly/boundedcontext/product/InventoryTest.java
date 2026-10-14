package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductFixture.SELLER_ID;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.clothingOptions;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.navy110;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryAdjustmentReason;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryAdjustmentRecord;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryOpening;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

    @Test
    @DisplayName("INV-19 초기 재고 10으로 재고를 열면 판매자가 남긴 INITIAL +10 기록이 하나 생긴다")
    void recordInitialStock() {

        InventoryOpening opening = InventoryOpening.open(option, 10, SELLER_ID);

        assertThat(opening.records())
            .extracting(InventoryAdjustmentRecord::getReason, InventoryAdjustmentRecord::getDeltaQty,
                InventoryAdjustmentRecord::getAdjustedBy)
            .containsExactly(tuple(InventoryAdjustmentReason.INITIAL, 10, SELLER_ID));
    }

    @Test
    @DisplayName("INV-19 초기 재고 0으로 재고를 열면 재고 조정 기록을 남기지 않는다")
    void noRecordForZeroInitialStock() {

        InventoryOpening opening = InventoryOpening.open(option, 0, SELLER_ID);

        assertThat(opening.records()).isEmpty();
    }

    @Test
    @DisplayName("INV-01 초기 재고가 0보다 작으면 재고를 열지 않는다")
    void rejectNegativeInitialStock() {

        assertThatThrownBy(() -> InventoryOpening.open(option, -1, SELLER_ID))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("옵션 없이는 재고를 열지 않는다")
    void rejectOpeningWithoutOption() {

        assertThatThrownBy(() -> InventoryOpening.open(null, 10, SELLER_ID))
            .isInstanceOf(NullPointerException.class);
    }
}
