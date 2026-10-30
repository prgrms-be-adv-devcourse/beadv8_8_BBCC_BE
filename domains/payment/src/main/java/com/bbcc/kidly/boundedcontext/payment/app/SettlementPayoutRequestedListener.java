package com.bbcc.kidly.boundedcontext.payment.app;

import com.bbcc.kidly.global.eventpublisher.DomainEventListener;
import com.bbcc.kidly.shared.settlement.event.SettlementPayoutRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * settlement.payout-requested를 Spring 이벤트로 받는다. 정산이 아직 Kafka로 보내지 않아 기본으로 뜬다(#607).
 * 정산용 Kafka 수신 스위치(kidly.payment.kafka.settlement-consumer.enabled)를 켜면 뜨지 않고 Kafka 리스너(in.event)가 받는다.
 * 같은 이벤트를 양쪽에서 받지 않는다. 처리는 PaymentEventHandler에 있다.
 */
@Component
@ConditionalOnProperty(name = "kidly.payment.kafka.settlement-consumer.enabled", havingValue = "false",
    matchIfMissing = true)
@RequiredArgsConstructor
public class SettlementPayoutRequestedListener {

    private final PaymentEventHandler handler;

    @DomainEventListener
    public void on(SettlementPayoutRequestedEvent event) {
        handler.settlementPayoutRequested(event);
    }
}
