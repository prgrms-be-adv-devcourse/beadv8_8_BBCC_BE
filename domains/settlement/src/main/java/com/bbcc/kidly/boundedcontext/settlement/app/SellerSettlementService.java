package com.bbcc.kidly.boundedcontext.settlement.app;

import com.bbcc.kidly.boundedcontext.settlement.app.dto.SettlementDetailResult;
import com.bbcc.kidly.boundedcontext.settlement.app.dto.SettlementSearchRequest;
import com.bbcc.kidly.boundedcontext.settlement.app.port.SettlementSearch;
import com.bbcc.kidly.boundedcontext.settlement.app.port.SettlementSummaryQuery;
import com.bbcc.kidly.boundedcontext.settlement.app.port.SettlementSummaryRow;
import com.bbcc.kidly.boundedcontext.settlement.app.port.SettlementTargetQuery;
import com.bbcc.kidly.boundedcontext.settlement.app.port.SettlementTargetRow;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementItemRepository;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementRepository;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SellerSettlementService {

    private final SettlementTargetQuery settlementTargetQuery;
    private final SettlementSummaryQuery settlementSummaryQuery;
    private final SettlementRepository settlementRepository;
    private final SettlementItemRepository settlementItemRepository;

    @Transactional(readOnly = true)
    public Page<SettlementTargetRow> getTargets(Long sellerId, Pageable pageable) {
        return settlementTargetQuery.findPending(sellerId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<SettlementSummaryRow> getSettlements(
        Long sellerId, SettlementSearchRequest request, Pageable pageable) {
        var status = request.status() == null ? null : SettlementStatus.valueOf(request.status());
        var search = new SettlementSearch(sellerId, status, request.fromOn(), request.toOn());
        return settlementSummaryQuery.findAll(search, pageable);
    }

    @Transactional(readOnly = true)
    public SettlementDetailResult getSettlement(Long sellerId, Long settlementId) {
        var settlement = settlementRepository.getByIdAndSellerId(settlementId, sellerId);
        var items = settlementItemRepository.findAllBySettlementIdOrderByIdAsc(settlement.getId());
        return SettlementDetailResult.of(settlement, items);
    }
}
