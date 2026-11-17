package com.bbcc.kidly.boundedcontext.product.out.outbox;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.bbcc.kidly.boundedcontext.product.app.port.outbox.ProductEventSendException;
import com.bbcc.kidly.boundedcontext.product.app.port.outbox.ProductEventSender;
import com.bbcc.kidly.boundedcontext.product.domain.outbox.ProductOutboxMessage;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.kafka.core.KafkaTemplate;

@RequiredArgsConstructor
public class KafkaProductEventSender implements ProductEventSender, DisposableBean {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final Duration sendTimeout;

    @Override
    public void send(ProductOutboxMessage message) {
        var record = new ProducerRecord<>(message.getTopic(), message.getMessageKey(), message.getPayload());
        record.headers()
            .add("eventType", message.getEventType().getBytes(UTF_8))
            .add("eventId", message.getEventId().toString().getBytes(UTF_8));
        try {
            kafkaTemplate.send(record).get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw failure(message, "응답 대기 시간 %dms 초과".formatted(sendTimeout.toMillis()), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw failure(message, "응답 대기 중 인터럽트", e);
        } catch (ExecutionException | RuntimeException e) {
            // 보내기 전 실패는 "Send failed", 보낸 뒤 실패는 "Failed to send"로 Spring이 감싸므로 Kafka 클라이언트 예외를 꺼낸다
            var cause = NestedExceptionUtils.getMostSpecificCause(e);
            throw failure(message, cause.getMessage(), cause);
        }
    }

    private ProductEventSendException failure(ProductOutboxMessage message, String reason, Throwable cause) {
        return new ProductEventSendException(
            "상품 이벤트를 Kafka로 보냈는지 확인하지 못했습니다. eventType=%s eventId=%s key=%s reason=%s"
                .formatted(message.getEventType(), message.getEventId(), message.getMessageKey(), reason),
            cause);
    }

    @Override
    public void destroy() {
        kafkaTemplate.destroy();
    }
}
