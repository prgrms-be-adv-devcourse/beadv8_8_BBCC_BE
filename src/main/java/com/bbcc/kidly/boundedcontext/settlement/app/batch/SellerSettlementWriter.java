package com.bbcc.kidly.boundedcontext.settlement.app.batch;

import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementCalculator;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementItemRepository;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementItemStatus;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementRepository;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementRunRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;

/**
 * 판매자마다 대기 항목을 읽어 정산서를 만들고 항목을 그 정산서로 정산한다.
 * 묶음이 실패하면 Batch가 같은 판매자 ID를 1명씩 새 트랜잭션에서 다시 넘기므로, 항목은 매번 새로 읽는다.
 */
@RequiredArgsConstructor
public class SellerSettlementWriter implements ItemWriter<Long> {

    private final SettlementRunRepository settlementRunRepository;
    private final SettlementItemRepository settlementItemRepository;
    private final SettlementRepository settlementRepository;
    private final BigDecimal feeRate;
    private final Long runId;
    private final SettlementCalculator settlementCalculator = new SettlementCalculator();

    @Override
    public void write(Chunk<? extends Long> sellerIds) {
        var run = settlementRunRepository.getById(runId);
        var now = LocalDateTime.now();
        for (var sellerId : sellerIds) {
            var items = settlementItemRepository.findAllBySellerIdAndStatusAndConfirmedAtBefore(
                sellerId, SettlementItemStatus.PENDING, run.getCutoffAt());
            if (items.isEmpty()) {
                continue;
            }
            var settlement = settlementRepository.save(settlementCalculator.calculate(run, items, feeRate, now));
            items.forEach(item -> item.settle(settlement.getId()));
        }
        settlementItemRepository.flush();
    }
}
