package com.bbcc.kidly.boundedcontext.settlement.domain;

import java.time.LocalDate;
import java.util.Optional;

/** 정산서 영속성 계약. */
public interface SettlementStatementRepository {

    SettlementStatement save(SettlementStatement statement);

    Optional<SettlementStatement> findById(Long id);

    Optional<SettlementStatement> findBySellerIdAndPeriodStartOn(Long sellerId, LocalDate periodStartOn);
}
