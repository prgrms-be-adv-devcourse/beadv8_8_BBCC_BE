package com.bbcc.kidly.boundedcontext.product.app.inventory.support;

import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryDeductionRejection;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryDeductionRepository;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryDeductionRequest;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.product.event.StockDeductionFailedEvent;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class InventoryDeductionRecorder {

    private final InventoryDeductionRepository inventoryDeductionRepository;
    private final EventPublisher eventPublisher;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailed(InventoryDeductionRequest request) {
        inventoryDeductionRepository.saveAll(request.failedRecords());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordRejected(InventoryDeductionRequest request, InventoryDeductionRejection rejection) {
        inventoryDeductionRepository.saveAll(request.failedRecords());
        var failures = new ArrayList<StockDeductionFailedEvent.Failure>();
        for (InventoryDeductionRejection.Failure failure : rejection.failures()) {
            failures.add(new StockDeductionFailedEvent.Failure(failure.orderItemId(), failure.reason()));
        }
        eventPublisher.publish(new StockDeductionFailedEvent(
            UUID.randomUUID(), LocalDateTime.now(),
            new StockDeductionFailedEvent.Data(request.orderId(), rejection.reason(), failures)));
    }
}
