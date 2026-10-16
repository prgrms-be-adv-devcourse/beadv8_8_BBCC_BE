package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.InventoryDeductionFixture.NAVY_110_ID;
import static com.bbcc.kidly.boundedcontext.product.InventoryDeductionFixture.NAVY_120_ID;
import static com.bbcc.kidly.boundedcontext.product.InventoryDeductionFixture.ORDER_ID;
import static com.bbcc.kidly.boundedcontext.product.InventoryDeductionFixture.item;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.bbcc.kidly.boundedcontext.product.InventoryDeductionFixture.Options;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryDeduction;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryDeductionRequest;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryDeductionStatus;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryRestoration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class InventoryRestorationTest {

    @Test
    @DisplayName("INV-12 주문의 DEDUCTED 차감 기록을 옵션 ID 오름차순으로, 같은 옵션 안에서는 주문상품 ID 오름차순으로 고른다")
    void pickDeductedRecordsInOptionIdOrder() {
        Options options = InventoryDeductionFixture.onSaleOptions();
        // 옵션 ID가 큰 110(7)을 먼저, 같은 옵션 안에서도 주문상품 ID가 큰 것을 먼저 주문해서 기록도 그 순서로 만들어진다
        List<InventoryDeduction> records = deductedRecords(ORDER_ID,
            item(13L, options.navy110(), 1),
            item(11L, options.navy110(), 2),
            item(12L, options.navy120(), 1));

        List<InventoryDeduction> targets = InventoryRestoration.of(ORDER_ID, records).recordsToRestore();

        assertThat(targets)
            .extracting(InventoryDeduction::getOrderItemId, InventoryDeduction::getProductOptionId)
            .containsExactly(
                tuple(12L, NAVY_120_ID),
                tuple(11L, NAVY_110_ID),
                tuple(13L, NAVY_110_ID));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = InventoryDeductionStatus.class, names = {"FAILED", "RESTORED"})
    @DisplayName("INV-04 FAILED와 RESTORED 차감 기록은 되돌리지 않는다")
    void skipRecordsNotDeducted(InventoryDeductionStatus status) {
        Options options = InventoryDeductionFixture.onSaleOptions();
        List<InventoryDeduction> records = InventoryDeductionFixture.withStatus(deductedRecords(ORDER_ID,
            item(11L, options.navy110(), 2),
            item(12L, options.navy120(), 1)), status);

        assertThat(InventoryRestoration.of(ORDER_ID, records).recordsToRestore()).isEmpty();
    }

    @Test
    @DisplayName("INV-04 상태가 섞인 기록에서는 DEDUCTED 기록만 되돌린다")
    void pickOnlyDeductedAmongMixedStatuses() {
        Options options = InventoryDeductionFixture.onSaleOptions();
        List<InventoryDeduction> records = new ArrayList<>();
        records.addAll(deductedRecords(ORDER_ID, item(11L, options.navy110(), 2)));
        records.addAll(InventoryDeductionFixture.withStatus(
            deductedRecords(ORDER_ID, item(12L, options.navy120(), 1)), InventoryDeductionStatus.RESTORED));
        records.addAll(InventoryDeductionFixture.withStatus(
            deductedRecords(ORDER_ID, item(13L, options.navy120(), 1)), InventoryDeductionStatus.FAILED));

        assertThat(InventoryRestoration.of(ORDER_ID, records).recordsToRestore())
            .extracting(InventoryDeduction::getOrderItemId)
            .containsExactly(11L);
    }

    @Test
    @DisplayName("INV-09 주문상품의 차감 기록이 하나도 없으면 주문 취소됨이 주문 생성됨보다 먼저 온 주문이다")
    void cancelledBeforeCreatedWhenNoRecord() {
        InventoryRestoration restoration = InventoryRestoration.of(ORDER_ID, List.of());

        assertThat(restoration.isCancelledBeforeCreated()).isTrue();
        assertThat(restoration.recordsToRestore()).isEmpty();
    }

    @Test
    @DisplayName("INV-09 주문상품의 차감 기록이 있으면 주문 취소됨이 먼저 온 주문이 아니다")
    void notCancelledBeforeCreatedWhenRecordExists() {
        Options options = InventoryDeductionFixture.onSaleOptions();
        List<InventoryDeduction> records = deductedRecords(ORDER_ID, item(11L, options.navy110(), 2));

        assertThat(InventoryRestoration.of(ORDER_ID, records).isCancelledBeforeCreated()).isFalse();
    }

    @ParameterizedTest(name = "모두 {0}이면 {1}")
    @CsvSource({"FAILED, true", "RESTORED, false", "DEDUCTED, false"})
    @DisplayName("INV-09 기록이 모두 FAILED인 주문만 재고 부족이나 판매중지로 거절한 주문이다")
    void rejectedOnlyWhenAllFailed(InventoryDeductionStatus status, boolean rejected) {
        Options options = InventoryDeductionFixture.onSaleOptions();
        List<InventoryDeduction> records = InventoryDeductionFixture.withStatus(deductedRecords(ORDER_ID,
            item(11L, options.navy110(), 2),
            item(12L, options.navy120(), 1)), status);

        assertThat(InventoryRestoration.of(ORDER_ID, records).isRejected()).isEqualTo(rejected);
    }

    @Test
    @DisplayName("다른 주문의 차감 기록이 섞여 있으면 복구 대상을 만들지 않는다")
    void rejectRecordsOfOtherOrder() {
        Options options = InventoryDeductionFixture.onSaleOptions();
        List<InventoryDeduction> records = new ArrayList<>();
        records.addAll(deductedRecords(ORDER_ID, item(11L, options.navy110(), 2)));
        records.addAll(deductedRecords(ORDER_ID + 1, item(12L, options.navy120(), 1)));

        assertThatThrownBy(() -> InventoryRestoration.of(ORDER_ID, records))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("차감 기록 목록이 없으면 복구 대상을 만들지 않는다")
    void rejectNullRecords() {
        assertThatThrownBy(() -> InventoryRestoration.of(ORDER_ID, null))
            .isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest(name = "되돌린 수량 {0}, 복구 뒤 수량 {1}")
    @CsvSource({"1, 1", "3, 3"})
    @DisplayName("SALE-08 복구 전 판매 가능 수량이 0이던 옵션은 복구로 품절이 풀린 옵션으로 고른다")
    void pickOptionReleasedFromSoldOut(int restoredQty, int availableQtyAfter) {
        List<Long> released = InventoryRestoration.soldOutReleasedOptionIds(
            Map.of(NAVY_110_ID, restoredQty), Map.of(NAVY_110_ID, availableQtyAfter));

        assertThat(released).containsExactly(NAVY_110_ID);
    }

    @ParameterizedTest(name = "되돌린 수량 {0}, 복구 뒤 수량 {1}")
    @CsvSource({"1, 2", "2, 5"})
    @DisplayName("SALE-08 복구 전에도 판매 가능 수량이 남아 있던 옵션은 고르지 않는다")
    void skipOptionNotSoldOutBeforeRestore(int restoredQty, int availableQtyAfter) {
        List<Long> released = InventoryRestoration.soldOutReleasedOptionIds(
            Map.of(NAVY_110_ID, restoredQty), Map.of(NAVY_110_ID, availableQtyAfter));

        assertThat(released).isEmpty();
    }

    @Test
    @DisplayName("SALE-08 여러 옵션을 되돌리면 품절이 풀린 옵션만 옵션 ID 오름차순으로 고른다")
    void pickReleasedOptionsInOptionIdOrder() {
        List<Long> released = InventoryRestoration.soldOutReleasedOptionIds(
            Map.of(30L, 1, 10L, 2, 20L, 1),
            Map.of(30L, 1, 10L, 2, 20L, 4));

        assertThat(released).containsExactly(10L, 30L);
    }

    @Test
    @DisplayName("되돌린 수량이나 복구 뒤 수량이 없으면 품절이 풀린 옵션을 고르지 않는다")
    void rejectNullQuantities() {
        assertThatThrownBy(() -> InventoryRestoration.soldOutReleasedOptionIds(null, Map.of()))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> InventoryRestoration.soldOutReleasedOptionIds(Map.of(), null))
            .isInstanceOf(NullPointerException.class);
    }

    private static List<InventoryDeduction> deductedRecords(long orderId, InventoryDeductionRequest.Item... items) {
        return InventoryDeductionRequest.of(orderId, List.of(items)).deductedRecords();
    }
}
