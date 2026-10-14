package com.bbcc.kidly.boundedcontext.settlement.out;

import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementStatement;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementStatementRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaSettlementStatementRepository
        extends JpaRepository<SettlementStatement, Long>, SettlementStatementRepository {
}
