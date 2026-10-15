package com.bbcc.kidly.boundedcontext.settlement.app;

import com.bbcc.kidly.boundedcontext.settlement.app.port.SettlementTargetQuery;
import com.bbcc.kidly.boundedcontext.settlement.app.port.SettlementTargetRow;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SellerSettlementService {

    private final SettlementTargetQuery settlementTargetQuery;

    @Transactional(readOnly = true)
    public Page<SettlementTargetRow> getTargets(Long sellerId, Pageable pageable) {
        return settlementTargetQuery.findPending(sellerId, pageable);
    }
}
