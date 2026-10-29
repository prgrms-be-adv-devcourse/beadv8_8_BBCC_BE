package com.bbcc.kidly.boundedcontext.product.out.outbox;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.bbcc.kidly.boundedcontext.product.app.port.outbox.ProductEventSendException;
import com.bbcc.kidly.boundedcontext.product.app.port.outbox.ProductEventSender;
import com.bbcc.kidly.boundedcontext.product.domain.outbox.ProductOutboxMessage;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * 아웃박스 메시지를 저장된 토픽과 키 그대로 Kafka로 보낸다. 본문은 저장할 때 만든 JSON을 바꾸지 않고 보낸다.
 * 헤더는 eventType과 eventId 두 개만 붙인다.
 */
public class KafkaProductEventSender implements ProductEventSender, DisposableBean {

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
            kafkaTemplate.send(record).get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (ExecutionException e) {
            Throwable cause = rootCause(e);
            throw failure(message, cause.getMessage(), cause);
        } catch (TimeoutException e) {
            throw failure(message, "응답 대기 시간 %dms 초과".formatted(sendTimeout.toMillis()), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw failure(message, e.getMessage(), e);
        } catch (RuntimeException e) {
            throw failure(message, e.getMessage(), e);
        }
    }

    // Spring이 감싼 예외(KafkaProducerException)가 아니라 Kafka 클라이언트가 알려 준 가장 안쪽 원인을 찾는다
    private static Throwable rootCause(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    // 예외 메시지는 로그에 남으므로 본문 대신 eventType · eventId와 원인만 담는다
    private ProductEventSendException failure(ProductOutboxMessage message, String reason, Throwable cause) {
        return new ProductEventSendException(
            "상품 이벤트를 Kafka로 보내지 못했습니다. eventType=%s eventId=%s reason=%s"
                .formatted(message.getEventType(), message.getEventId(), reason),
            cause);
    }

    // 상품 전용 템플릿은 빈이 아니라 Spring이 닫아 주지 않으므로, 앱이 내려갈 때 직접 닫는다
    @Override
    public void destroy() {
        kafkaTemplate.destroy();
    }
}
