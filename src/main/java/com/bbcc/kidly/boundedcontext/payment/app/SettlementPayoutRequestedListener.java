package com.bbcc.kidly.boundedcontext.payment.app;

import com.bbcc.kidly.boundedcontext.payment.app.dto.PayoutCommand;
import com.bbcc.kidly.global.eventpublisher.DomainEventListener;
import com.bbcc.kidly.shared.settlement.event.SettlementPayoutRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * settlement.payout-requested를 받아 판매자 예치금에 정산금액을 입금한다. 이벤트 필드가 바뀌면 여기 변환만 고친다.
 */
@Component
@RequiredArgsConstructor
public class SettlementPayoutRequestedListener {

    private final SettlementPayoutService settlementPayoutService;

    @DomainEventListener
    public void on(SettlementPayoutRequestedEvent event) {
        SettlementPayoutRequestedEvent.Data data = event.data();
        settlementPayoutService.payout(new PayoutCommand(data.statementId(), data.sellerId(), data.amount()));
    }
}
