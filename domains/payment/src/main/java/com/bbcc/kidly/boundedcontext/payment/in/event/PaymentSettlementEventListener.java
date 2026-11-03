package com.bbcc.kidly.boundedcontext.payment.in.event;

import com.bbcc.kidly.boundedcontext.payment.app.PaymentEventHandler;
import com.bbcc.kidly.shared.settlement.event.SettlementPayoutRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * kidly.settlement.events에서 settlement.payout-requested를 받아 판매자 예치금에 입금한다(#607). 정산의 다른 종류는 건너뛴다.
 * 별도 스위치(kidly.payment.kafka.settlement-consumer.enabled, 기본 true)로 켠다. 결제 서비스는 정산과 다른 JVM이라 이 리스너로만 받는다(#525).
 * 끄면 시작하지 않고 Spring 리스너(app/SettlementPayoutRequestedListener)가 받는다. 같은 JVM 안에서 정산이 Spring 이벤트로 보내는 테스트용이다.
 */
@Component
@RequiredArgsConstructor
public class PaymentSettlementEventListener {

    private final PaymentEventHandler handler;
    private final JsonMapper jsonMapper;

    @KafkaListener(id = "paymentSettlementEventListener", topics = PaymentKafkaListenerConfig.SETTLEMENT_EVENTS,
        groupId = PaymentKafkaListenerConfig.GROUP, containerFactory = "paymentKafkaListenerContainerFactory",
        autoStartup = PaymentKafkaListenerConfig.SETTLEMENT_AUTO_STARTUP)
    public void on(ConsumerRecord<String, String> record) {
        switch (PaymentKafkaListenerConfig.eventType(record)) {
            case "settlement.payout-requested" -> handler.settlementPayoutRequested(
                jsonMapper.readValue(record.value(), SettlementPayoutRequestedEvent.class));
            default -> { }  // 결제가 받지 않는 종류
        }
    }
}
