package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.InventoryDeductionFixture.NAVY_110_ID;
import static com.bbcc.kidly.boundedcontext.product.InventoryDeductionFixture.ORDER_ID;
import static com.bbcc.kidly.boundedcontext.product.InventoryDeductionFixture.item;
import static com.bbcc.kidly.boundedcontext.product.OrderCancelledFixture.cancelledItem;
import static com.bbcc.kidly.boundedcontext.product.OrderCancelledFixture.orderCancelled;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

import com.bbcc.kidly.boundedcontext.product.InventoryDeductionFixture.Options;
import com.bbcc.kidly.boundedcontext.product.app.inventory.InventoryRestorationService;
import com.bbcc.kidly.boundedcontext.product.app.inventory.support.InventoryDeductionRecorder;
import com.bbcc.kidly.boundedcontext.product.app.inventory.support.InventoryRestorer;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryDeduction;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryDeductionRepository;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryDeductionRequest;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class InventoryRestorationServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private InventoryDeductionRepository inventoryDeductionRepository;

    @Mock
    private InventoryRestorer inventoryRestorer;

    @Mock
    private InventoryDeductionRecorder inventoryDeductionRecorder;

    @InjectMocks
    private InventoryRestorationService inventoryRestorationService;

    @Test
    @DisplayName("INV-09 차감 기록이 없어 남기려던 FAILED가 그사이 커밋된 차감 기록에 막히면, 기록을 다시 읽어 그 차감을 되돌린다")
    void restoreDeductionCommittedWhileRecordingFailed() {
        Options options = InventoryDeductionFixture.onSaleOptions();
        List<InventoryDeduction> committedWhileRecordingFailed = InventoryDeductionRequest.of(ORDER_ID,
            List.of(item(11L, options.navy110(), 2))).deductedRecords();
        given(inventoryDeductionRepository.findAllByOrderItemIdIn(List.of(11L)))
            .willReturn(List.of(), committedWhileRecordingFailed);
        given(productRepository.findOptionsWithProduct(List.of(NAVY_110_ID))).willReturn(List.of(options.navy110()));
        willThrow(new DataIntegrityViolationException("주문상품 11의 차감 기록이 이미 있습니다"))
            .given(inventoryDeductionRecorder).recordFailed(any());

        inventoryRestorationService.restore(orderCancelled(ORDER_ID, cancelledItem(11L, options.navy110(), 2)));

        then(inventoryRestorer).should().restore(committedWhileRecordingFailed);
    }
}
