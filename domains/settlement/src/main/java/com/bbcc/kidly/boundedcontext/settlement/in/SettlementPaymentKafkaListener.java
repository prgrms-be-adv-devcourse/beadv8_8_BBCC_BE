package com.bbcc.kidly.boundedcontext.settlement.in;

import com.bbcc.kidly.boundedcontext.settlement.app.SettlementPayoutResultService;
import com.bbcc.kidly.shared.payment.event.PaymentPayoutCompletedEvent;
import com.bbcc.kidly.shared.payment.event.PaymentPayoutFailedEvent;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * kidly.payment.events에서 payment.payout-completed · payment.payout-failed를 받아 정산서에 반영한다. 결제의 다른 종류는 건너뛴다.
 * 정산서가 없거나 판매자 · 금액이 다른 결과는 IllegalStateException으로 재시도 없이 DLT로 간다(SettlementPayoutResultService).
 * 스위치(kidly.settlement.kafka.consumer.enabled)가 꺼져 있으면 시작하지 않고, 그때는 Spring 리스너(SettlementPaymentEventListener)가 받는다.
 */
@Component
@RequiredArgsConstructor
public class SettlementPaymentKafkaListener {

    private final SettlementPayoutResultService settlementPayoutResultService;
    private final JsonMapper jsonMapper;

    @KafkaListener(id = "settlementPaymentKafkaListener", topics = SettlementKafkaListenerConfig.PAYMENT_EVENTS,
        groupId = SettlementKafkaListenerConfig.GROUP, containerFactory = "settlementKafkaListenerContainerFactory",
        autoStartup = SettlementKafkaListenerConfig.AUTO_STARTUP)
    public void on(ConsumerRecord<String, String> record) {
        switch (SettlementKafkaListenerConfig.eventType(record)) {
            case "payment.payout-completed" -> settlementPayoutResultService.markPaid(
                jsonMapper.readValue(record.value(), PaymentPayoutCompletedEvent.class).data());
            case "payment.payout-failed" -> settlementPayoutResultService.markPaymentFailed(
                jsonMapper.readValue(record.value(), PaymentPayoutFailedEvent.class).data());
            default -> { }  // 정산이 받지 않는 종류
        }
    }
}
