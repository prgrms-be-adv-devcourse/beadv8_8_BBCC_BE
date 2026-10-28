package com.bbcc.kidly.boundedcontext.settlement.app;

import com.bbcc.kidly.boundedcontext.settlement.app.dto.AdminSettlementSearchRequest;
import com.bbcc.kidly.boundedcontext.settlement.app.port.SettlementSearch;
import com.bbcc.kidly.boundedcontext.settlement.app.port.SettlementSummaryQuery;
import com.bbcc.kidly.boundedcontext.settlement.app.port.SettlementSummaryRow;
import com.bbcc.kidly.boundedcontext.settlement.app.support.SettlementPayoutRequester;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementRepository;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementStatus;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminSettlementService {

    private final SettlementSummaryQuery settlementSummaryQuery;
    private final SettlementRepository settlementRepository;
    private final SettlementPayoutRequester settlementPayoutRequester;

    @Transactional(readOnly = true)
    public Page<SettlementSummaryRow> getSettlements(AdminSettlementSearchRequest request, Pageable pageable) {
        var status = request.status() == null ? null : SettlementStatus.valueOf(request.status());
        var search = new SettlementSearch(request.sellerId(), status, request.fromOn(), request.toOn());
        return settlementSummaryQuery.findAll(search, pageable);
    }

    // 같은 정산서 ID로 다시 요청한다. 동시에 와도 결제가 한 번만 입금하고 정산서는 @Version으로 한 번만 바뀐다
    @Transactional
    public void retryPayout(Long settlementId) {
        var settlement = settlementRepository.getById(settlementId);
        settlement.validatePayoutRetryable();
        settlementPayoutRequester.request(settlement, LocalDateTime.now());
    }
}
