package com.bbcc.kidly.boundedcontext.product.domain.inventory;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface InventoryDeductionRepository {

    List<InventoryDeduction> findAllByOrderItemIdIn(Collection<Long> orderItemIds);

    <S extends InventoryDeduction> List<S> saveAll(Iterable<S> records);

    boolean restoreIfDeducted(long deductionId, LocalDateTime now);
}
