package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.InventoryDeductionFixture.NAVY_110_ID;
import static com.bbcc.kidly.boundedcontext.product.InventoryDeductionFixture.ORDER_ID;
import static com.bbcc.kidly.boundedcontext.product.InventoryDeductionFixture.beforeSaleOptions;
import static com.bbcc.kidly.boundedcontext.product.InventoryDeductionFixture.item;
import static com.bbcc.kidly.boundedcontext.product.InventoryDeductionFixture.onSaleOptions;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.bbcc.kidly.boundedcontext.product.InventoryDeductionFixture.Options;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryDeductionRejection;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryDeductionRequest;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductOptionStatus;
import com.bbcc.kidly.shared.product.domain.StockDeductionFailureCode;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

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

    @ParameterizedTest(name = "옵션 {0}")
    @EnumSource(value = ProductOptionStatus.class, names = {"STOPPED", "ARCHIVED"})
    @DisplayName("INV-13 상품은 판매중인데 옵션이 판매중이 아니면 그 옵션을 담은 주문상품만 OPTION_NOT_ON_SALE로 거절한다")
    void optionNotOnSale(ProductOptionStatus optionStatus) {
        Options options = onSaleOptions();
        // 옵션을 판매중지 · 보관하는 업무 메서드가 아직 없어 상태 칸을 직접 바꾼다
        ReflectionTestUtils.setField(options.navy120(), "saleStatus", optionStatus);
        InventoryDeductionRequest request = InventoryDeductionRequest.of(ORDER_ID, List.of(
            item(11L, options.navy110(), 2),
            item(12L, options.navy120(), 1)));

        InventoryDeductionRejection rejection = request.notOnSaleRejection();

        assertThat(rejection.failures())
            .extracting(InventoryDeductionRejection.Failure::orderItemId, InventoryDeductionRejection.Failure::reason)
            .containsExactly(tuple(12L, StockDeductionFailureCode.OPTION_NOT_ON_SALE));
    }

    @Test
    @DisplayName("INV-11 재고가 모자란 옵션을 담은 주문상품은 모두 OUT_OF_STOCK로 거절하고, 다른 옵션의 주문상품은 담지 않는다")
    void outOfStock() {
        Options options = onSaleOptions();
        InventoryDeductionRequest request = InventoryDeductionRequest.of(ORDER_ID, List.of(
            item(11L, options.navy110(), 2),
            item(12L, options.navy120(), 1),
            item(13L, options.navy110(), 1)));

        InventoryDeductionRejection rejection = request.shortageRejection(Set.of(NAVY_110_ID));

        assertThat(rejection.failures())
            .extracting(InventoryDeductionRejection.Failure::orderItemId, InventoryDeductionRejection.Failure::reason)
            .containsExactly(
                tuple(11L, StockDeductionFailureCode.OUT_OF_STOCK),
                tuple(13L, StockDeductionFailureCode.OUT_OF_STOCK));
    }
}
