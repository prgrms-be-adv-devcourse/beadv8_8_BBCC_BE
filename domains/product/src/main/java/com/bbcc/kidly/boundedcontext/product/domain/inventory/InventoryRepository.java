package com.bbcc.kidly.boundedcontext.product.domain.inventory;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;

public interface InventoryRepository {

    void saveOpening(InventoryOpening opening);

    void restore(long optionId, int qty, LocalDateTime now);

    boolean deductIfAvailable(long optionId, int qty, LocalDateTime now);

    boolean adjustIfAvailable(long optionId, int deltaQty, LocalDateTime now);

    void saveAdjustmentRecord(InventoryAdjustmentRecord record);

    Map<Long, Integer> findAvailableQtyByOptionId(Collection<Long> optionIds);

    void deleteByOptionIds(Collection<Long> optionIds);
}
