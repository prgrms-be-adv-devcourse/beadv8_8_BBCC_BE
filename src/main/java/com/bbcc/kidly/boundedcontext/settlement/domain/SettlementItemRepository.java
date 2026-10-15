package com.bbcc.kidly.boundedcontext.settlement.domain;

import java.util.Optional;

public interface SettlementItemRepository {

    SettlementItem save(SettlementItem item);

    Optional<SettlementItem> findById(Long id);

    boolean existsByOrderItemId(Long orderItemId);
}
