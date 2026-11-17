package com.bbcc.kidly.boundedcontext.product.app.inventory.support;

import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryDeductionRepository;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryDeductionRequest;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryRepository;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.product.event.StockDeductedEvent;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class InventoryDeductor {

    private final InventoryRepository inventoryRepository;
    private final InventoryDeductionRepository inventoryDeductionRepository;
    private final EventPublisher eventPublisher;
    private final SoldOutPublisher soldOutPublisher;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deduct(InventoryDeductionRequest request) {
        LocalDateTime now = LocalDateTime.now();
        var shortOptionIds = new HashSet<Long>();
        for (InventoryDeductionRequest.OptionQuantity quantity : request.optionQuantities()) {
            if (!inventoryRepository.deductIfAvailable(quantity.optionId(), quantity.qty(), now)) {
                shortOptionIds.add(quantity.optionId());
            }
        }
        if (!shortOptionIds.isEmpty()) {
            throw new InventoryShortageException(shortOptionIds);
        }
        inventoryDeductionRepository.saveAll(request.deductedRecords());
        eventPublisher.publish(new StockDeductedEvent(
            UUID.randomUUID(), now, new StockDeductedEvent.Data(request.orderId())));
        soldOutPublisher.publish(request);
    }
}
