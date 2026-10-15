package com.bbcc.kidly.boundedcontext.settlement.app.batch;

import static java.util.Objects.requireNonNull;

import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementCalculator;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementItemRepository;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementItemStatus;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementPeriod;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;

/**
 * 판매자마다 대기 항목을 읽어 정산서를 만들고 항목을 그 정산서로 정산한다.
 * 묶음이 실패하면 Batch가 같은 판매자 ID를 1명씩 새 트랜잭션에서 다시 넘기므로, 항목은 매번 새로 읽는다.
 */
public class SettlementSellerWriter implements ItemWriter<Long> {

    private final SettlementItemRepository settlementItemRepository;
    private final SettlementRepository settlementRepository;
    private final SettlementCalculator settlementCalculator;
    private final SettlementPeriod period;

    public SettlementSellerWriter(
        SettlementItemRepository settlementItemRepository, SettlementRepository settlementRepository,
        BigDecimal feeRate, LocalDateTime cutoffAt) {
        this.settlementItemRepository = requireNonNull(settlementItemRepository, "settlementItemRepository는 필수입니다");
        this.settlementRepository = requireNonNull(settlementRepository, "settlementRepository는 필수입니다");
        this.settlementCalculator = new SettlementCalculator(feeRate);
        this.period = SettlementPeriod.from(cutoffAt);
    }

    @Override
    public void write(Chunk<? extends Long> sellerIds) {
        var now = LocalDateTime.now();
        for (var sellerId : sellerIds) {
            var items = settlementItemRepository.findAllBySellerIdAndStatusAndConfirmedAtBefore(
                sellerId, SettlementItemStatus.PENDING, period.cutoffAt());
            if (items.isEmpty()) {
                continue;
            }
            var settlement = settlementRepository.save(settlementCalculator.calculate(period, items, now));
            items.forEach(item -> item.settle(settlement.getId()));
        }
        settlementItemRepository.flush();
    }
}
