package com.bbcc.kidly.boundedcontext.settlement.app.batch;

import static java.util.Objects.requireNonNull;

import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementCalculator;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementItemRepository;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementPeriod;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementRepository;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.settlement.event.SettlementPayoutRequestedEvent;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;

/**
 * 정산 계획마다 정산서를 만들고 대상을 그 정산서로 정산한 뒤 지급을 요청한다. 0원 정산서도 요청한다.
 * 묶음이 실패하면 Batch가 같은 계획을 1명씩 새 트랜잭션에서 다시 넘기므로, 대상은 매번 새로 읽는다.
 */
public class SettlementSellerWriter implements ItemWriter<SettlementSellerPlan> {

    private final SettlementItemRepository settlementItemRepository;
    private final SettlementRepository settlementRepository;
    private final SettlementCalculator settlementCalculator;
    private final SettlementPeriod period;
    private final EventPublisher eventPublisher;

    public SettlementSellerWriter(
        SettlementItemRepository settlementItemRepository, SettlementRepository settlementRepository,
        SettlementCalculator settlementCalculator, SettlementPeriod period, EventPublisher eventPublisher) {
        this.settlementItemRepository = requireNonNull(settlementItemRepository, "settlementItemRepository는 필수입니다");
        this.settlementRepository = requireNonNull(settlementRepository, "settlementRepository는 필수입니다");
        this.settlementCalculator = requireNonNull(settlementCalculator, "settlementCalculator는 필수입니다");
        this.period = requireNonNull(period, "period는 필수입니다");
        this.eventPublisher = requireNonNull(eventPublisher, "eventPublisher는 필수입니다");
    }

    // 계획 뒤에 대상이 바뀌었으면 IllegalStateException
    @Override
    public void write(Chunk<? extends SettlementSellerPlan> plans) {
        var now = LocalDateTime.now(SettlementPeriod.SERVICE_ZONE);
        for (var plan : plans) {
            var items = settlementItemRepository.findAllByIdIn(plan.itemIds());
            if (items.size() != plan.itemIds().size()) {
                throw new IllegalStateException("정산 대상이 바뀌었습니다");
            }
            var settlement = settlementRepository.save(
                settlementCalculator.create(period, plan.sellerId(), plan.totalSalesAmount(), now));
            items.forEach(item -> item.settle(settlement.getId()));
            eventPublisher.publish(new SettlementPayoutRequestedEvent(UUID.randomUUID(),
                LocalDateTime.now(SettlementPeriod.SERVICE_ZONE),
                new SettlementPayoutRequestedEvent.Data(settlement.getId(), settlement.getSellerId(),
                    settlement.getSettlementAmount(), settlement.getPeriodStartOn(), settlement.getPeriodEndOn())));
        }
        settlementItemRepository.flush();
    }
}
