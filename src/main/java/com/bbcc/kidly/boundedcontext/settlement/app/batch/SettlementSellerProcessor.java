package com.bbcc.kidly.boundedcontext.settlement.app.batch;

import static java.util.Objects.requireNonNull;

import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementCalculator;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementItem;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementItemRepository;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementItemStatus;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementPeriod;
import org.springframework.batch.infrastructure.item.ItemProcessor;

/**
 * 판매자의 대기 항목을 읽어 판매 합계를 구하고 정산 계획으로 만든다. 정산할 항목이 없으면 null로 걸러낸다.
 */
public class SettlementSellerProcessor implements ItemProcessor<Long, SettlementSellerPlan> {

    private final SettlementItemRepository settlementItemRepository;
    private final SettlementCalculator settlementCalculator;
    private final SettlementPeriod period;

    public SettlementSellerProcessor(
        SettlementItemRepository settlementItemRepository, SettlementCalculator settlementCalculator,
        SettlementPeriod period) {
        this.settlementItemRepository = requireNonNull(settlementItemRepository, "settlementItemRepository는 필수입니다");
        this.settlementCalculator = requireNonNull(settlementCalculator, "settlementCalculator는 필수입니다");
        this.period = requireNonNull(period, "period는 필수입니다");
    }

    @Override
    public SettlementSellerPlan process(Long sellerId) {
        var items = settlementItemRepository.findAllBySellerIdAndStatusAndConfirmedAtBefore(
            sellerId, SettlementItemStatus.PENDING, period.cutoffAt());
        if (items.isEmpty()) {
            return null;
        }
        var total = settlementCalculator.sumSalesAmount(period, items);
        var itemIds = items.stream().map(SettlementItem::getId).sorted().toList();
        return new SettlementSellerPlan(sellerId, itemIds, total);
    }
}
