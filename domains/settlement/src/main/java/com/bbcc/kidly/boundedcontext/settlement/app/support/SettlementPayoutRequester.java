package com.bbcc.kidly.boundedcontext.settlement.app.support;

import com.bbcc.kidly.boundedcontext.settlement.domain.Settlement;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.settlement.event.SettlementPayoutRequestedEvent;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 정산서의 지급을 결제에 요청한다(settlement.payout-requested). 정산 작업, 관리자 재요청, 자동 재요청(SettlementPayoutRetryScheduler)이 함께 쓴다.
 * 결제는 같은 정산서 ID로 한 번만 입금하고, 다시 오면 저장된 결과를 다시 보낸다.
 */
@Component
@RequiredArgsConstructor
public class SettlementPayoutRequester {

    private final EventPublisher eventPublisher;

    public void request(Settlement settlement, LocalDateTime now) {
        eventPublisher.publish(new SettlementPayoutRequestedEvent(UUID.randomUUID(), now,
            new SettlementPayoutRequestedEvent.Data(settlement.getId(), settlement.getSellerId(),
                settlement.getSettlementAmount(), settlement.getPeriodStartOn(), settlement.getPeriodEndOn())));
    }
}
