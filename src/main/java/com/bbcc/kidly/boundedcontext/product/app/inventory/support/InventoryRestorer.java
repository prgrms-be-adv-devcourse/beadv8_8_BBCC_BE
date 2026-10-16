package com.bbcc.kidly.boundedcontext.product.app.inventory.support;

import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryDeduction;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryDeductionRepository;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryRepository;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryRestoration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 차감 기록의 RESTORED 표시와 재고 수량 복구를 하나의 새 트랜잭션으로 묶는다.
 * 복구로 품절이 풀린 옵션이 있으면 같은 트랜잭션에서 상품 정보 변경됨을 보낸다.
 * 호출한 쪽의 트랜잭션과 따로 커밋되거나 롤백되도록 별도 빈으로 둔다.
 */
@Component
@RequiredArgsConstructor
public class InventoryRestorer {

    private final InventoryRepository inventoryRepository;
    private final InventoryDeductionRepository inventoryDeductionRepository;
    private final SoldOutPublisher soldOutPublisher;

    // 주문의 차감 기록을 한 트랜잭션에서 모두 되돌린다. 하나라도 실패하면 먼저 되돌린 기록까지 함께 롤백된다 (INV-07)
    // 상태가 실제로 DEDUCTED에서 RESTORED로 바뀐 기록만 수량을 되돌려, 같은 복구가 두 번 일어나도 재고는 한 번만 늘어난다 (INV-05)
    // 복구로 품절이 풀린 옵션이 있으면 같은 트랜잭션에서 상품 정보 변경됨을 보낸다 (SALE-08)
    // 재고 행의 판매 상태는 보지 않는다. 판매중지된 옵션이어도 차감한 수량은 돌려놓는다 (INV-14)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void restore(List<InventoryDeduction> targets) {
        LocalDateTime now = LocalDateTime.now();
        // targets는 호출한 쪽에서 읽은 다른 영속성 컨텍스트의 엔티티라 값만 꺼내 쓴다
        List<Target> values = new ArrayList<>();
        for (InventoryDeduction record : targets) {
            values.add(new Target(record.getId(), record.getProductOptionId(), record.getQty()));
        }
        Map<Long, Integer> restoredQtyByOptionId = new HashMap<>();
        for (Target target : values) {
            if (inventoryDeductionRepository.restoreIfDeducted(target.deductionId(), now)) {
                inventoryRepository.restore(target.optionId(), target.qty(), now);
                restoredQtyByOptionId.merge(target.optionId(), target.qty(), Integer::sum);
            }
        }
        publishSoldOutReleased(restoredQtyByOptionId);
    }

    // 복구 UPDATE가 재고 행을 잠근 채라 여기서 읽는 복구 뒤 수량은 다른 트랜잭션에 바뀌지 않는다
    private void publishSoldOutReleased(Map<Long, Integer> restoredQtyByOptionId) {
        if (restoredQtyByOptionId.isEmpty()) {
            return;
        }
        Map<Long, Integer> availableQtyByOptionId =
            inventoryRepository.findAvailableQtyByOptionId(restoredQtyByOptionId.keySet());
        List<Long> releasedOptionIds =
            InventoryRestoration.soldOutReleasedOptionIds(restoredQtyByOptionId, availableQtyByOptionId);
        if (!releasedOptionIds.isEmpty()) {
            soldOutPublisher.publishAfterRestoration(releasedOptionIds);
        }
    }

    private record Target(long deductionId, long optionId, int qty) {
    }
}
