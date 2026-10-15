package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.OrderCreatedFixture.orderCreated;
import static com.bbcc.kidly.boundedcontext.product.OrderCreatedFixture.orderItem;
import static com.bbcc.kidly.boundedcontext.product.ProductFixture.SELLER_ID;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.product.ProductOptionSaver.OnSaleOptions;
import com.bbcc.kidly.boundedcontext.product.app.inventory.InventoryAdjustmentService;
import com.bbcc.kidly.boundedcontext.product.app.inventory.InventoryDeductionService;
import com.bbcc.kidly.boundedcontext.product.app.inventory.dto.InventoryAdjustmentRequest;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.shared.order.event.OrderCreatedEvent;
import com.bbcc.kidly.support.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 재고 조정과 주문의 재고 차감을 실제 스레드로 동시에 실행해, 같은 재고 행을 함께 바꿔도 수량이 맞는지 확인한다.
 * 모든 스레드가 준비된 뒤 출발 신호 하나로 함께 시작한다. 테스트에 @Transactional을 붙이지 않는다.
 */
@IntegrationTest
class InventoryAdjustmentConcurrencyIntegrationTest {

    private static final long ORDER_ID = 100L;

    @Autowired
    private InventoryAdjustmentService inventoryAdjustmentService;

    @Autowired
    private InventoryDeductionService inventoryDeductionService;

    @Autowired
    private ProductOptionSaver productOptionSaver;

    @Autowired
    private InventoryDeductionDb inventoryDeductionDb;

    @AfterEach
    void checkInvariants() {
        inventoryDeductionDb.assertInvariants();
    }

    @Test
    @DisplayName("INV-02 판매 가능 수량 5인 옵션에 분실 -5 조정과 1개짜리 주문 5건이 동시에 와도 수량이 0 아래로 내려가지 않고 수량 보존식이 맞는다")
    void adjustWhileDeducting() throws Exception {
        OnSaleOptions options = productOptionSaver.saveOnSaleNavy110And120(5, 5);
        AtomicBoolean adjusted = new AtomicBoolean();
        List<Runnable> tasks = new ArrayList<>();
        tasks.add(() -> {
            try {
                inventoryAdjustmentService.adjust(
                    SELLER_ID, options.navy110().getId(), new InventoryAdjustmentRequest(-5, "LOST"));
                adjusted.set(true);
            } catch (BusinessException e) {
                // 차감이 먼저 줄였으면 판매 가능 수량보다 많이 줄이는 조정이라 거절된다
            }
        });
        for (int i = 1; i <= 5; i++) {
            OrderCreatedEvent event = orderCreated(ORDER_ID + i, orderItem(1000L + i, options.navy110(), 1));
            tasks.add(() -> inventoryDeductionService.deduct(event));
        }

        ConcurrentTasks.runConcurrently(tasks);

        int availableQty = inventoryDeductionDb.availableQty(options.navy110());
        long deductedCount = inventoryDeductionDb.countByStatus("DEDUCTED");
        assertThat(availableQty).isNotNegative();
        if (adjusted.get()) {
            // 조정이 먼저 5를 모두 뺐으면 뒤에 온 주문은 모두 재고 부족으로 거절된다
            assertThat(deductedCount).isZero();
            assertThat(availableQty).isZero();
            return;
        }
        assertThat(availableQty).isEqualTo(5 - deductedCount);
    }
}
