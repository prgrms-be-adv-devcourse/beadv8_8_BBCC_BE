package com.bbcc.kidly.boundedcontext.product.app.inventory.support;

import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryDeductionRepository;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryDeductionRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 거절한 차감 요청의 FAILED 차감 기록을 새 트랜잭션으로 저장한다.
 * 롤백된 차감 트랜잭션과 따로 커밋되도록 별도 빈으로 둔다.
 */
@Component
@RequiredArgsConstructor
public class InventoryDeductionRecorder {

    private final InventoryDeductionRepository inventoryDeductionRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailed(InventoryDeductionRequest request) {
        inventoryDeductionRepository.saveAll(request.failedRecords());
    }
}
