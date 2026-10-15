package com.bbcc.kidly.boundedcontext.settlement.domain;

import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.global.error.CommonErrorCode;
import java.util.Optional;

public interface SettlementRunRepository {

    SettlementRun save(SettlementRun run);

    Optional<SettlementRun> findById(Long id);

    default SettlementRun getById(Long id) {
        return findById(id).orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }
}
