package com.bbcc.kidly.boundedcontext.activity.out.messaging;

import com.bbcc.kidly.boundedcontext.activity.app.port.CommunityEventSender;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * 커뮤니티 이벤트를 kidly.community.events로 보낸다(Boot 기본 KafkaTemplate). 키는 이벤트의 correlationId, 헤더는 eventType ·
 * eventId 둘이고 Java 타입 헤더(__TypeId__)는 넣지 않는다(본문은 이미 만든 JSON 문자열). 브로커가 받을 때까지 기다리고, 실패는
 * 예외 종류만 담아 던진다(키 · 본문을 넣지 않는다).
 */
@Component
public class KafkaCommunityEventSender implements CommunityEventSender {

    public static final String TOPIC = "kidly.community.events";

    private final KafkaTemplate<String, String> kafkaTemplate;

    public KafkaCommunityEventSender(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void send(String key, String eventType, UUID eventId, String payload, Duration timeout) {
        ProducerRecord<String, String> record = new ProducerRecord<>(TOPIC, key, payload);
        record.headers().add("eventType", eventType.getBytes(StandardCharsets.UTF_8));
        record.headers().add("eventId", eventId.toString().getBytes(StandardCharsets.UTF_8));
        try {
            kafkaTemplate.send(record).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SendFailedException(e.getClass().getSimpleName());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new SendFailedException(cause.getClass().getSimpleName());
        } catch (TimeoutException | RuntimeException e) {
            throw new SendFailedException(e.getClass().getSimpleName());
        }
    }
}
