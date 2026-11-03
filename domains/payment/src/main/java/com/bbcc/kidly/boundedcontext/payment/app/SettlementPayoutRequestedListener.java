package com.bbcc.kidly.boundedcontext.payment.app;

import com.bbcc.kidly.global.eventpublisher.DomainEventListener;
import com.bbcc.kidly.shared.settlement.event.SettlementPayoutRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * settlement.payout-requested를 Spring 이벤트로 받는다(#607).
 * 정산용 Kafka 수신 스위치(kidly.payment.kafka.settlement-consumer.enabled)를 끈 때만 뜬다.
 * 결제 서비스는 정산과 다른 JVM이라 운영에서는 켜 두고 Kafka 리스너(in.event)가 받는다(#525). 끄는 것은 같은 JVM 테스트용이다.
 * 같은 이벤트를 양쪽에서 받지 않는다. 처리는 PaymentEventHandler에 있다.
 */
@Component
@ConditionalOnProperty(name = "kidly.payment.kafka.settlement-consumer.enabled", havingValue = "false")
@RequiredArgsConstructor
public class SettlementPayoutRequestedListener {

    private final PaymentEventHandler handler;

    @DomainEventListener
    public void on(SettlementPayoutRequestedEvent event) {
        handler.settlementPayoutRequested(event);
    }
}
