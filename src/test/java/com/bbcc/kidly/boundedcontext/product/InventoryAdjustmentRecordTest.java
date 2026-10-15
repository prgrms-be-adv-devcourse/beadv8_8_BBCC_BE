package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductFixture.SELLER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryAdjustmentReason;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryAdjustmentRecord;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductOption;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class InventoryAdjustmentRecordTest {

    private static final ProductOption OPTION = InventoryDeductionFixture.onSaleOptions().navy110();

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({"RESTOCK, 3", "DEFECTIVE, -3", "LOST, -3", "CORRECTION, 3", "CORRECTION, -3"})
    @DisplayName("INV-18 사유와 증감량 부호가 맞으면 판매자의 재고 조정 기록을 만든다")
    void adjustWhenSignMatchesReason(InventoryAdjustmentReason reason, int deltaQty) {
        InventoryAdjustmentRecord record = InventoryAdjustmentRecord.adjust(
            new InventoryAdjustmentRecord.AdjustInfo(OPTION, deltaQty, reason, SELLER_ID));

        assertThat(record)
            .extracting(
                InventoryAdjustmentRecord::getReason,
                InventoryAdjustmentRecord::getDeltaQty,
                InventoryAdjustmentRecord::getAdjustedBy)
            .containsExactly(reason, deltaQty, SELLER_ID);
    }

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({"RESTOCK, -3", "DEFECTIVE, 3", "LOST, 3"})
    @DisplayName("INV-18 사유와 증감량 부호가 맞지 않으면 재고 조정 기록을 만들지 않는다")
    void rejectWhenSignDoesNotMatchReason(InventoryAdjustmentReason reason, int deltaQty) {
        assertThatThrownBy(() -> InventoryAdjustmentRecord.adjust(
            new InventoryAdjustmentRecord.AdjustInfo(OPTION, deltaQty, reason, SELLER_ID)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = InventoryAdjustmentReason.class, names = {"RESTOCK", "DEFECTIVE", "LOST", "CORRECTION"})
    @DisplayName("INV-18 증감량이 0이면 어느 사유든 재고 조정 기록을 만들지 않는다")
    void rejectZeroDelta(InventoryAdjustmentReason reason) {
        assertThatThrownBy(() -> InventoryAdjustmentRecord.adjust(
            new InventoryAdjustmentRecord.AdjustInfo(OPTION, 0, reason, SELLER_ID)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("증감량은 0이 아니어야 합니다");
    }

    @Test
    @DisplayName("INV-19 판매자의 재고 조정은 옵션을 등록할 때만 남기는 INITIAL 사유를 받지 않는다")
    void rejectInitialReason() {
        assertThatThrownBy(() -> InventoryAdjustmentRecord.adjust(
            new InventoryAdjustmentRecord.AdjustInfo(OPTION, 3, InventoryAdjustmentReason.INITIAL, SELLER_ID)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("adjustInfosWithoutRequiredValue")
    @DisplayName("옵션 · 사유 · 조정한 판매자가 없으면 재고 조정 기록을 만들지 않는다")
    void rejectWithoutRequiredValue(InventoryAdjustmentRecord.AdjustInfo info) {
        assertThatThrownBy(() -> InventoryAdjustmentRecord.adjust(info))
            .isInstanceOf(NullPointerException.class);
    }

    static Stream<Named<InventoryAdjustmentRecord.AdjustInfo>> adjustInfosWithoutRequiredValue() {
        return Stream.of(
            Named.of("조정 정보 없음", null),
            Named.of("옵션 없음",
                new InventoryAdjustmentRecord.AdjustInfo(null, 3, InventoryAdjustmentReason.RESTOCK, SELLER_ID)),
            Named.of("사유 없음", new InventoryAdjustmentRecord.AdjustInfo(OPTION, 3, null, SELLER_ID)),
            Named.of("조정한 판매자 없음",
                new InventoryAdjustmentRecord.AdjustInfo(OPTION, 3, InventoryAdjustmentReason.RESTOCK, null)));
    }
}
