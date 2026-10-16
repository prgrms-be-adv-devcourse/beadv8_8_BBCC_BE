package com.bbcc.kidly.boundedcontext.settlement.domain;

import java.util.List;

public interface SettlementSkipRepository {

    SettlementSkip save(SettlementSkip skip);

    List<SettlementSkip> findAllByJobExecutionIdOrderByIdAsc(Long jobExecutionId);
}
