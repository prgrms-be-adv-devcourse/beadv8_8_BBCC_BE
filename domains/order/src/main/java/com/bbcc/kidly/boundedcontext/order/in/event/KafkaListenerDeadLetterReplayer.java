package com.bbcc.kidly.boundedcontext.order.in.event;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.bbcc.kidly.boundedcontext.order.app.DeadLetterReplay;
import com.bbcc.kidly.boundedcontext.order.app.deadletter.UnknownDeadLetterSourceException;
import com.bbcc.kidly.boundedcontext.order.app.port.DeadLetterReplayer;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.record.TimestampType;
import org.springframework.stereotype.Component;

/**
 * 저장한 DLT 메시지로 원래 Kafka 메시지를 다시 만들어 원래 토픽의 리스너에 그대로 넘긴다(#601).
 * 읽기 · 종류 분기 · 처리 완료 표시(주문 자체 보관) · 멱등 처리가 Kafka로 받았을 때와 같다. 원래 토픽에 다시 넣지 않는다.
 */
@Component
@RequiredArgsConstructor
class KafkaListenerDeadLetterReplayer implements DeadLetterReplayer {

    private final ProductEventKafkaListener productListener;
    private final SellerEventKafkaListener sellerListener;
    private final ChildProfileEventKafkaListener childProfileListener;
    private final PaymentEventKafkaListener paymentListener;

    @Override
    public DeadLetterReplay.Outcome replay(DeadLetterReplay message) {
        ConsumerRecord<String, String> record = record(message);
        boolean handedOff = EventHandOffs.watch(() -> {
            switch (message.sourceTopic()) {
                case OrderKafkaListenerConfig.PRODUCT_EVENTS -> productListener.on(record);
                case OrderKafkaListenerConfig.SELLER_EVENTS -> sellerListener.on(record);
                case OrderKafkaListenerConfig.CHILDPROFILE_EVENTS -> childProfileListener.on(record);
                case OrderKafkaListenerConfig.PAYMENT_EVENTS -> paymentListener.on(record);
                default -> throw new UnknownDeadLetterSourceException(message.sourceTopic());
            }
        });
        return handedOff ? DeadLetterReplay.Outcome.HANDED_OFF : DeadLetterReplay.Outcome.PROCESSED;
    }

    // 원래 위치 · 키 · 본문, 원래 헤더 eventType · eventId(없던 헤더는 넣지 않는다)
    private static ConsumerRecord<String, String> record(DeadLetterReplay message) {
        RecordHeaders headers = new RecordHeaders();
        if (message.eventType() != null) {
            headers.add(OrderKafkaMessages.EVENT_TYPE, message.eventType().getBytes(UTF_8));
        }
        if (message.eventId() != null) {
            headers.add(OrderKafkaMessages.EVENT_ID, message.eventId().getBytes(UTF_8));
        }
        return new ConsumerRecord<>(message.sourceTopic(), message.sourcePartition(), message.sourceOffset(),
            ConsumerRecord.NO_TIMESTAMP, TimestampType.NO_TIMESTAMP_TYPE, ConsumerRecord.NULL_SIZE,
            ConsumerRecord.NULL_SIZE, message.messageKey(), message.payload(), headers, Optional.empty());
    }
}
