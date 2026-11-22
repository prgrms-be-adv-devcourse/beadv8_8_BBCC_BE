package com.bbcc.kidly.boundedcontext.settlement.in;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.bbcc.kidly.boundedcontext.settlement.app.port.SettlementDeadLetterReplay;
import com.bbcc.kidly.boundedcontext.settlement.app.port.SettlementDeadLetters.Message;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Component;

/**
 * 원래 토픽(kidly.order.events, kidly.payment.events)은 다른 도메인도 받으므로 다시 보내지 않는다.
 * 보관한 토픽과 키, 본문, 헤더로 메시지를 만들어 정산 리스너의 처리 메서드를 직접 부른다.
 */
@Component
@RequiredArgsConstructor
public class SettlementDeadLetterReplayer implements SettlementDeadLetterReplay {

    private final SettlementOrderKafkaListener orderListener;
    private final SettlementPaymentKafkaListener paymentListener;

    @Override
    public void replay(Message message) {
        var record = new ConsumerRecord<>(message.sourceTopic(), 0, 0L, message.messageKey(), message.payload());
        if (message.eventType() != null) {
            record.headers().add(SettlementKafkaListenerConfig.EVENT_TYPE, message.eventType().getBytes(UTF_8));
        }
        if (message.eventId() != null) {
            record.headers().add(SettlementKafkaListenerConfig.EVENT_ID, message.eventId().getBytes(UTF_8));
        }
        switch (message.sourceTopic()) {
            case SettlementKafkaListenerConfig.ORDER_EVENTS -> orderListener.on(record);
            case SettlementKafkaListenerConfig.PAYMENT_EVENTS -> paymentListener.on(record);
            default -> throw new IllegalArgumentException("재처리할 수 없는 정산 이벤트입니다.");
        }
    }
}
