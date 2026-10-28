package com.bbcc.kidly.boundedcontext.settlement.in;

import com.bbcc.kidly.boundedcontext.settlement.app.SettlementPayoutResultService;
import com.bbcc.kidly.global.eventpublisher.DomainEventListener;
import com.bbcc.kidly.shared.payment.event.PaymentPayoutCompletedEvent;
import com.bbcc.kidly.shared.payment.event.PaymentPayoutFailedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 결제가 정산금액을 입금했거나 입금하지 못했다는 결과를 정산서에 반영한다.
 */
@Component
@RequiredArgsConstructor
public class SettlementPaymentEventListener {

    private final SettlementPayoutResultService settlementPayoutResultService;

    @DomainEventListener
    public void on(PaymentPayoutCompletedEvent event) {
        settlementPayoutResultService.markPaid(event.data());
    }

    @DomainEventListener
    public void on(PaymentPayoutFailedEvent event) {
        settlementPayoutResultService.markPaymentFailed(event.data());
    }
}
