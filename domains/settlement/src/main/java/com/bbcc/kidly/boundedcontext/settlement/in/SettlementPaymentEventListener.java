package com.bbcc.kidly.boundedcontext.settlement.in;

import com.bbcc.kidly.boundedcontext.settlement.app.SettlementPayoutResultService;
import com.bbcc.kidly.global.eventpublisher.DomainEventListener;
import com.bbcc.kidly.shared.payment.event.PaymentPayoutCompletedEvent;
import com.bbcc.kidly.shared.payment.event.PaymentPayoutFailedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 결제가 정산금액을 입금했거나 입금하지 못했다는 결과를 정산서에 반영한다.
 * Spring 이벤트로 받는다. Kafka 수신 스위치(kidly.settlement.kafka.consumer.enabled)를 끈 경우에만 뜬다.
 * 켜져 있으면 Kafka 리스너(SettlementPaymentKafkaListener)가 받으므로, 같은 이벤트를 양쪽에서 받지 않는다.
 */
@Component
@ConditionalOnProperty(name = "kidly.settlement.kafka.consumer.enabled", havingValue = "false")
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
