package com.bbcc.kidly.boundedcontext.settlement.out;

import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementItem;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementItemRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaSettlementItemRepository
        extends JpaRepository<SettlementItem, Long>, SettlementItemRepository {
}
