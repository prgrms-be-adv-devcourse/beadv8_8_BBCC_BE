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
 * 정산이 아직 Kafka로 보내지 않아 별도 스위치(kidly.payment.kafka.settlement-consumer.enabled, 기본 false)로 꺼 둔다.
 * 꺼져 있으면 시작하지 않아 토픽이 없어도 구독하지 않고, 그동안 Spring 리스너(app/SettlementPayoutRequestedListener)가 받는다.
 * 정산의 Kafka 발행이 배포되기 전에 켜면 지급 요청이 어디에도 도착하지 않는다.
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
