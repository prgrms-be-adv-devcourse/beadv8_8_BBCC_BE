package com.bbcc.kidly.boundedcontext.product.app.inventory.support;

import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryDeductionRejection;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryDeductionRepository;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryDeductionRequest;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.product.event.StockDeductionFailedEvent;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 거절한 차감 요청의 FAILED 차감 기록을 새 트랜잭션으로 저장한다. 거절한 주문은 재고 부족함도 보낸다.
 * 롤백된 차감 트랜잭션과 따로 커밋되도록 별도 빈으로 둔다.
 */
@Component
@RequiredArgsConstructor
public class InventoryDeductionRecorder {

    private final InventoryDeductionRepository inventoryDeductionRepository;
    private final EventPublisher eventPublisher;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailed(InventoryDeductionRequest request) {
        inventoryDeductionRepository.saveAll(request.failedRecords());
    }

    // 거절한 주문의 FAILED 기록을 저장하고, 같은 트랜잭션에서 재고 부족함을 보낸다
    // 기록이 커밋될 때만 이벤트가 나가고, 기록이 UNIQUE 제약에 막혀 롤백되면 이벤트도 나가지 않는다
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordRejected(InventoryDeductionRequest request, InventoryDeductionRejection rejection) {
        inventoryDeductionRepository.saveAll(request.failedRecords());
        List<StockDeductionFailedEvent.Failure> failures = new ArrayList<>();
        for (InventoryDeductionRejection.Failure failure : rejection.failures()) {
            failures.add(new StockDeductionFailedEvent.Failure(failure.orderItemId(), failure.reason()));
        }
        eventPublisher.publish(new StockDeductionFailedEvent(
            UUID.randomUUID(), LocalDateTime.now(),
            new StockDeductionFailedEvent.Data(request.orderId(), rejection.reason(), failures)));
    }
}
