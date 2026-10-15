package com.bbcc.kidly.boundedcontext.product.app.inventory.support;

import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryDeductionRepository;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryDeductionRequest;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryRepository;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.product.event.StockDeductedEvent;
import java.time.LocalDateTime;
import java.util.UUID;
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
    private final EventPublisher eventPublisher;
    private final SoldOutPublisher soldOutPublisher;

    // 옵션 ID 오름차순으로 옵션별 수량을 줄이고, 줄인 뒤 같은 트랜잭션에서 DEDUCTED 기록을 저장한다
    // 한 옵션이라도 모자라면 예외를 던져 먼저 줄인 옵션까지 모두 되돌린다
    // 차감이 끝나면 같은 트랜잭션에서 재고 차감됨을 발행해, 차감이 커밋될 때만 주문에 전달한다
    // 줄인 옵션이 품절이 됐으면 같은 트랜잭션에서 그 상품의 상품 정보 변경됨도 발행한다 (SALE-08)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deduct(InventoryDeductionRequest request) {
        LocalDateTime now = LocalDateTime.now();
        for (InventoryDeductionRequest.OptionQuantity quantity : request.optionQuantities()) {
            if (!inventoryRepository.deductIfAvailable(quantity.optionId(), quantity.qty(), now)) {
                throw new InventoryShortageException(quantity.optionId(), quantity.qty());
            }
        }
        inventoryDeductionRepository.saveAll(request.deductedRecords());
        eventPublisher.publish(new StockDeductedEvent(
            UUID.randomUUID(), now, new StockDeductedEvent.Data(request.orderId())));
        soldOutPublisher.publish(request);
    }
}
