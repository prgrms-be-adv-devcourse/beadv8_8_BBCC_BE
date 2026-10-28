package com.bbcc.kidly.boundedcontext.settlement.out;

import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementSkip;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementSkipRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaSettlementSkipRepository extends JpaRepository<SettlementSkip, Long>, SettlementSkipRepository {
}
