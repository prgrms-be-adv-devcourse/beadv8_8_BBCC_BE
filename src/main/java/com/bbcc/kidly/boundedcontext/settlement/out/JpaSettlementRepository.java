package com.bbcc.kidly.boundedcontext.settlement.out;

import com.bbcc.kidly.boundedcontext.settlement.domain.Settlement;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaSettlementRepository extends JpaRepository<Settlement, Long>, SettlementRepository {
}
