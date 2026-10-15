package com.bbcc.kidly.boundedcontext.product.app.inventory.support;

import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryDeduction;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryDeductionRepository;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 차감 기록의 RESTORED 표시와 재고 수량 복구를 하나의 새 트랜잭션으로 묶는다.
 * 호출한 쪽의 트랜잭션과 따로 커밋되거나 롤백되도록 별도 빈으로 둔다.
 */
@Component
@RequiredArgsConstructor
public class InventoryRestorer {

    private final InventoryRepository inventoryRepository;
    private final InventoryDeductionRepository inventoryDeductionRepository;

    // 주문의 차감 기록을 한 트랜잭션에서 모두 되돌린다. 하나라도 실패하면 먼저 되돌린 기록까지 함께 롤백된다 (INV-07)
    // 상태가 실제로 DEDUCTED에서 RESTORED로 바뀐 기록만 수량을 되돌려, 같은 복구가 두 번 일어나도 재고는 한 번만 늘어난다 (INV-05)
    // 재고 행의 판매 상태는 보지 않는다. 판매중지된 옵션이어도 차감한 수량은 돌려놓는다 (INV-14)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void restore(List<InventoryDeduction> targets) {
        LocalDateTime now = LocalDateTime.now();
        // targets는 호출한 쪽에서 읽은 다른 영속성 컨텍스트의 엔티티라 값만 꺼내 쓴다
        List<Target> values = new ArrayList<>();
        for (InventoryDeduction record : targets) {
            values.add(new Target(record.getId(), record.getProductOptionId(), record.getQty()));
        }
        for (Target target : values) {
            if (inventoryDeductionRepository.restoreIfDeducted(target.deductionId(), now)) {
                inventoryRepository.restore(target.optionId(), target.qty(), now);
            }
        }
    }

    private record Target(long deductionId, long optionId, int qty) {
    }
}
