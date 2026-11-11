package com.bbcc.kidly.boundedcontext.settlement.app;

import com.bbcc.kidly.boundedcontext.settlement.app.dto.SellerWithdrawalBlockerResult;
import com.bbcc.kidly.boundedcontext.settlement.app.dto.SettlementDetailResult;
import com.bbcc.kidly.boundedcontext.settlement.app.dto.SettlementSearchRequest;
import com.bbcc.kidly.boundedcontext.settlement.app.dto.SettlementTargetResult;
import com.bbcc.kidly.boundedcontext.settlement.app.port.SettlementHolidayLookupException;
import com.bbcc.kidly.boundedcontext.settlement.app.port.SettlementSearch;
import com.bbcc.kidly.boundedcontext.settlement.app.port.SettlementSummaryQuery;
import com.bbcc.kidly.boundedcontext.settlement.app.port.SettlementSummaryRow;
import com.bbcc.kidly.boundedcontext.settlement.app.port.SettlementTargetQuery;
import com.bbcc.kidly.boundedcontext.settlement.domain.SellerWithdrawalBlocker;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementItemRepository;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementItemStatus;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementRepository;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementStatus;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
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
    private final SettlementBusinessCalendar businessCalendar;

    // 예정일은 확정일 다음 영업일(#732). 공휴일을 조회하지 못하면 목록은 주되 예정일만 비운다
    @Transactional(readOnly = true)
    public Page<SettlementTargetResult> getTargets(Long sellerId, Pageable pageable) {
        var rows = settlementTargetQuery.findPending(sellerId, pageable);
        var dates = new HashMap<LocalDate, LocalDate>();
        try {
            return rows.map(row -> SettlementTargetResult.of(row,
                dates.computeIfAbsent(row.confirmedAt().toLocalDate(), businessCalendar::nextBusinessDayAfter)));
        } catch (SettlementHolidayLookupException e) {
            return rows.map(row -> SettlementTargetResult.of(row, (LocalDate) null));
        }
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

    // 지급 대기(COMPLETED)와 지급 실패(FAILED)는 돈이 아직 판매자에게 나가지 않았으므로 탈퇴를 막는다
    @Transactional(readOnly = true)
    public List<SellerWithdrawalBlockerResult> getWithdrawalBlockers(Long sellerId) {
        var blockers = SellerWithdrawalBlocker.of(
            settlementItemRepository.countBySellerIdAndStatus(sellerId, SettlementItemStatus.PENDING),
            settlementRepository.countBySellerIdAndStatusIn(
                sellerId, List.of(SettlementStatus.COMPLETED, SettlementStatus.FAILED)));
        return blockers.stream().map(SellerWithdrawalBlockerResult::from).toList();
    }
}
