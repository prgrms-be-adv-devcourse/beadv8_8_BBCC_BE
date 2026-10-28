package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.InventoryDeductionFixture.NAVY_110_ID;
import static com.bbcc.kidly.boundedcontext.product.InventoryDeductionFixture.NAVY_120_ID;
import static com.bbcc.kidly.boundedcontext.product.InventoryDeductionFixture.ORDER_ID;
import static com.bbcc.kidly.boundedcontext.product.InventoryDeductionFixture.beforeSaleOptions;
import static com.bbcc.kidly.boundedcontext.product.InventoryDeductionFixture.item;
import static com.bbcc.kidly.boundedcontext.product.InventoryDeductionFixture.onSaleOptions;
import static com.bbcc.kidly.boundedcontext.product.InventoryDeductionFixture.withStatus;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.bbcc.kidly.boundedcontext.product.InventoryDeductionFixture.Options;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryDeduction;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryDeductionRequest;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryDeductionRequest.OptionQuantity;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryDeductionStatus;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

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

    @ParameterizedTest(name = "수량 {0}")
    @ValueSource(ints = {0, -1})
    @DisplayName("수량이 0 이하인 주문상품은 차감 요청에 받지 않는다")
    void rejectNonPositiveQty(int qty) {
        assertThatThrownBy(() -> InventoryDeductionRequest.of(ORDER_ID, List.of(item(11L, options.navy110(), qty))))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("주문상품이 없으면 차감 요청을 만들지 않는다")
    void rejectEmptyItems() {
        assertThatThrownBy(() -> InventoryDeductionRequest.of(ORDER_ID, List.of()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("INV-10 주문상품의 기존 차감 기록이 없으면 처음 받은 요청이다")
    void firstReceivedWhenNoExistingRecord() {
        InventoryDeductionRequest request = InventoryDeductionRequest.of(ORDER_ID, List.of(
            item(11L, options.navy110(), 2),
            item(12L, options.navy120(), 1)));

        assertThat(request.isAlreadyHandled(List.of())).isFalse();
    }

    @ParameterizedTest(name = "기존 기록 {0}")
    @EnumSource(InventoryDeductionStatus.class)
    @DisplayName("INV-10 모든 주문상품의 기존 차감 기록이 옵션 · 수량까지 같으면 상태와 상관없이 이미 처리한 요청이다")
    void alreadyHandledWhenAllRecordsMatch(InventoryDeductionStatus status) {
        List<InventoryDeduction> firstRecords = withStatus(twoItemOrder(onSaleOptions()).deductedRecords(), status);

        assertThat(twoItemOrder(options).isAlreadyHandled(firstRecords)).isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("mismatchedFirstRecords")
    @DisplayName("INV-10 기존 차감 기록이 일부만 있거나 옵션 · 수량이 다르면 차감 요청을 처리하지 않는다")
    void rejectMismatchedRecords(String mismatch, List<InventoryDeduction> firstRecords) {
        assertThatThrownBy(() -> twoItemOrder(options).isAlreadyHandled(firstRecords))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // 같은 주문상품 ID로 처음 받았던 주문 생성됨의 차감 기록인데, 지금 요청과 어긋난 경우들이다
    static Stream<Arguments> mismatchedFirstRecords() {
        Options first = onSaleOptions();
        return Stream.of(
            Arguments.of("일부만 있음", InventoryDeductionRequest.of(ORDER_ID, List.of(
                item(11L, first.navy110(), 2))).deductedRecords()),
            Arguments.of("옵션이 다름", InventoryDeductionRequest.of(ORDER_ID, List.of(
                item(11L, first.navy120(), 2),
                item(12L, first.navy120(), 1))).deductedRecords()),
            Arguments.of("수량이 다름", InventoryDeductionRequest.of(ORDER_ID, List.of(
                item(11L, first.navy110(), 5),
                item(12L, first.navy120(), 1))).deductedRecords()));
    }

    @Test
    @DisplayName("INV-13 모든 주문상품의 옵션이 판매중이면 살 수 있는 요청이다")
    void purchasableWhenAllOptionsOnSale() {
        assertThat(twoItemOrder(options).isPurchasable()).isTrue();
    }

    @Test
    @DisplayName("INV-13 판매중이 아닌 옵션이 하나라도 있으면 살 수 없는 요청이다")
    void notPurchasableWhenAnyOptionNotOnSale() {
        InventoryDeductionRequest request = InventoryDeductionRequest.of(ORDER_ID, List.of(
            item(11L, options.navy110(), 2),
            item(12L, beforeSaleOptions().navy120(), 1)));

        assertThat(request.isPurchasable()).isFalse();
    }

    @Test
    @DisplayName("옵션이 없는 주문상품은 차감 요청에 받지 않는다")
    void rejectItemWithoutOption() {
        assertThatThrownBy(() -> InventoryDeductionRequest.of(ORDER_ID, List.of(item(11L, null, 1))))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("INV-01 같은 옵션의 수량 합계가 int 범위를 넘으면 차감 요청을 만들지 않는다")
    void rejectQtySumOverflow() {
        assertThatThrownBy(() -> InventoryDeductionRequest.of(ORDER_ID, List.of(
            item(11L, options.navy110(), 1_100_000_000),
            item(12L, options.navy110(), 1_100_000_000))))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // 주문상품 11(네이비 110, 2개)과 12(네이비 120, 1개)로 만든 차감 요청이다
    private static InventoryDeductionRequest twoItemOrder(Options options) {
        return InventoryDeductionRequest.of(ORDER_ID, List.of(
            item(11L, options.navy110(), 2),
            item(12L, options.navy120(), 1)));
    }
}
