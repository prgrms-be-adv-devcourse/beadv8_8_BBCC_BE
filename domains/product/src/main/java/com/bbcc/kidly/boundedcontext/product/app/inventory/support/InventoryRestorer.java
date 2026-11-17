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

@Component
@RequiredArgsConstructor
public class InventoryRestorer {

    private final InventoryRepository inventoryRepository;
    private final InventoryDeductionRepository inventoryDeductionRepository;
    private final SoldOutPublisher soldOutPublisher;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void restore(List<InventoryDeduction> targets) {
        LocalDateTime now = LocalDateTime.now();
        var values = new ArrayList<Target>();
        for (InventoryDeduction record : targets) {
            values.add(new Target(record.getId(), record.getProductOptionId(), record.getQty()));
        }
        var restoredQtyByOptionId = new HashMap<Long, Integer>();
        for (Target target : values) {
            if (inventoryDeductionRepository.restoreIfDeducted(target.deductionId(), now)) {
                inventoryRepository.restore(target.optionId(), target.qty(), now);
                restoredQtyByOptionId.merge(target.optionId(), target.qty(), Integer::sum);
            }
        }
        publishSoldOutReleased(restoredQtyByOptionId);
    }

    private void publishSoldOutReleased(Map<Long, Integer> restoredQtyByOptionId) {
        if (restoredQtyByOptionId.isEmpty()) {
            return;
        }
        Map<Long, Integer> availableQtyByOptionId =
            inventoryRepository.findAvailableQtyByOptionId(restoredQtyByOptionId.keySet());
        List<Long> releasedOptionIds =
            InventoryRestoration.soldOutReleasedOptionIds(restoredQtyByOptionId, availableQtyByOptionId);
        if (!releasedOptionIds.isEmpty()) {
            soldOutPublisher.publishStartedSaleProducts(releasedOptionIds);
        }
    }

    private record Target(long deductionId, long optionId, int qty) {
    }
}
