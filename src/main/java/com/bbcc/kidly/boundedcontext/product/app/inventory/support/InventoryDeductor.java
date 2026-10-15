package com.bbcc.kidly.boundedcontext.product.app.inventory.support;

import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryDeductionRepository;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryDeductionRequest;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 재고 차감과 DEDUCTED 차감 기록 저장을 하나의 새 트랜잭션으로 묶는다.
 * 호출한 쪽의 트랜잭션과 따로 커밋되거나 롤백되도록 별도 빈으로 둔다.
 */
@Component
@RequiredArgsConstructor
public class InventoryDeductor {

    private final InventoryRepository inventoryRepository;
    private final InventoryDeductionRepository inventoryDeductionRepository;

    // 옵션 ID 오름차순으로 옵션별 수량을 줄이고, 줄인 뒤 같은 트랜잭션에서 DEDUCTED 기록을 저장한다
    // 한 옵션이라도 모자라면 예외를 던져 먼저 줄인 옵션까지 모두 되돌린다
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public void deduct(InventoryDeductionRequest request) {
        LocalDateTime now = LocalDateTime.now();
        for (InventoryDeductionRequest.OptionQuantity quantity : request.optionQuantities()) {
            if (!inventoryRepository.deductIfAvailable(quantity.optionId(), quantity.qty(), now)) {
                throw new InventoryShortageException(quantity.optionId(), quantity.qty());
            }
        }
        inventoryDeductionRepository.saveAll(request.deductedRecords());
    }
}
