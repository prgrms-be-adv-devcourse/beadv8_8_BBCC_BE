package com.bbcc.kidly.boundedcontext.settlement.domain;

import java.util.Optional;

public interface SettlementRepository {

    Settlement save(Settlement settlement);

    Optional<Settlement> findById(Long id);
}
