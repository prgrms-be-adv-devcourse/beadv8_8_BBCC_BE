package com.bbcc.kidly.boundedcontext.product.out.outbox;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.bbcc.kidly.boundedcontext.product.app.port.outbox.ProductEventSendException;
import com.bbcc.kidly.boundedcontext.product.app.port.outbox.ProductEventSender;
import com.bbcc.kidly.boundedcontext.product.domain.outbox.ProductOutboxMessage;
import java.time.Duration;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * 아웃박스 메시지를 저장된 토픽과 키 그대로 Kafka로 보낸다. 본문은 저장할 때 만든 JSON을 바꾸지 않고 보낸다.
 * 헤더는 eventType과 eventId 두 개만 붙인다.
 */
public class KafkaProductEventSender implements ProductEventSender {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final Duration sendTimeout;

    public KafkaProductEventSender(KafkaTemplate<String, String> kafkaTemplate, Duration sendTimeout) {
        this.kafkaTemplate = kafkaTemplate;
        this.sendTimeout = sendTimeout;
    }

    @Override
    public void send(ProductOutboxMessage message) {
        ProducerRecord<String, String> record =
            new ProducerRecord<>(message.getTopic(), message.getMessageKey(), message.getPayload());
        record.headers()
            .add("eventType", message.getEventType().getBytes(UTF_8))
            .add("eventId", message.getEventId().toString().getBytes(UTF_8));
        try {
            kafkaTemplate.send(record);
        } catch (RuntimeException e) {
            throw failure(message, e);
        }
    }

    // 예외 메시지는 로그에 남으므로 본문 대신 eventType · eventId와 원인만 담는다
    private ProductEventSendException failure(ProductOutboxMessage message, Throwable cause) {
        return new ProductEventSendException(
            "상품 이벤트를 Kafka로 보내지 못했습니다. eventType=%s eventId=%s reason=%s"
                .formatted(message.getEventType(), message.getEventId(), cause.getMessage()),
            cause);
    }
}
