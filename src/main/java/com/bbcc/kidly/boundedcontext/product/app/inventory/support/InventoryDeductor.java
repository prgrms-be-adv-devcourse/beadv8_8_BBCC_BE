package com.bbcc.kidly.boundedcontext.product.app.inventory.support;

import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryDeductionRepository;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryDeductionRequest;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
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
    // 격리 수준은 DB 기본값인 READ COMMITTED를 따른다. 더 엄격한 수준이면 같은 옵션을 동시에 차감할 때 직렬화 오류로 정상 주문이 실패한다
    // 격리 수준을 직접 지정하지 않는 이유는, 커밋할 때 DB 연결을 반납하도록 Hibernate를 설정하면 Spring이 격리 수준을 지정한 트랜잭션을 거부하기 때문이다
    @Transactional(propagation = Propagation.REQUIRES_NEW)
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
