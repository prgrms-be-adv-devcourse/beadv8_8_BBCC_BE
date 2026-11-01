package com.bbcc.kidly.boundedcontext.order.out.messaging;

import com.bbcc.kidly.boundedcontext.order.app.port.OrderEventSender;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderOutboxRepository.OutboxMessage;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.ProducerListener;
import org.springframework.stereotype.Component;

/**
 * 아웃박스 메시지를 Kafka로 보낸다(#540). 헤더 eventType · eventId를 붙이고 브로커가 받았다고 답할 때까지 기다린다.
 * 주문 전용 템플릿은 Boot 프로듀서 설정(주소 · acks=all · 멱등, 앱 spring.kafka)을 그대로 쓰고 직렬화 · 대기 시간만 덮어쓴다.
 * 빈으로 등록하지 않으므로 Boot 기본 KafkaTemplate과 앱 spring.kafka 설정은 그대로다.
 * 대기 시간을 줄이는 이유: 릴레이는 스케줄러 스레드(앱 전체 1개) · DB 트랜잭션 · 릴레이 잠금을 쥔 채 보내고 기다린다.
 * 기본값(max.block 60초 · delivery 120초)이면 브로커가 죽었을 때 한 주기가 몇 분씩 다른 스케줄러와 DB 연결을 붙잡는다.
 * kidly.kafka.producer.enabled가 false면 만들지 않는다(Kafka 없이 도는 테스트). 값이 없으면 만든다.
 */
@Component
@ConditionalOnProperty(name = "kidly.kafka.producer.enabled", havingValue = "true", matchIfMissing = true)
class KafkaOrderEventSender implements OrderEventSender, DisposableBean {

    private static final int MAX_BLOCK_MS = 5_000;
    private static final int DELIVERY_TIMEOUT_MS = 10_000;

    /**
     * 메타데이터 · 버퍼 대기(max.block) + 전달 제한(delivery.timeout). 이 안에 끝나지 않으면 실패로 본다.
     */
    static final Duration SEND_TIMEOUT = Duration.ofMillis(MAX_BLOCK_MS + DELIVERY_TIMEOUT_MS);

    /**
     * 본문은 이미 JSON 문자열이라 문자열 그대로 보낸다(앱 기본도 String이지만 이 템플릿의 타입이므로 명시한다).
     * delivery.timeout은 request.timeout(+ linger 0) 이상이어야 한다.
     */
    private static final Map<String, Object> PRODUCER_OVERRIDES = Map.of(
        ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
        ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
        ProducerConfig.MAX_BLOCK_MS_CONFIG, MAX_BLOCK_MS,
        ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 5_000,
        ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, DELIVERY_TIMEOUT_MS);

    private final KafkaTemplate<String, String> kafkaTemplate;

    KafkaOrderEventSender(ProducerFactory<String, String> producerFactory) {
        this.kafkaTemplate = new KafkaTemplate<>(producerFactory, PRODUCER_OVERRIDES);
        // 기본 LoggingProducerListener는 실패 로그에 본문을 찍는다. 실패는 릴레이가 본문 없이 남긴다
        this.kafkaTemplate.setProducerListener(new ProducerListener<>() {
        });
    }

    @Override
    public void send(OutboxMessage message) {
        ProducerRecord<String, String> record = new ProducerRecord<>(message.topic(), message.messageKey(),
            message.payload());
        record.headers().add("eventType", message.eventType().getBytes(StandardCharsets.UTF_8));
        record.headers().add("eventId", message.eventId().toString().getBytes(StandardCharsets.UTF_8));
        try {
            kafkaTemplate.send(record).get(SEND_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (RuntimeException | ExecutionException e) {
            // 메타데이터 대기 시간 초과는 send()에서 바로 Spring KafkaException("Send failed")으로, 전달 실패는 get()에서 나온다.
            // 둘 다 바깥 예외 메시지에는 원인이 없어 Kafka 클라이언트 예외(예: TimeoutException ... after 5000 ms)를 담는다
            throw failed(kafkaClientCause(e));
        } catch (TimeoutException e) {
            throw failed(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw failed(e);
        }
    }

    /**
     * 주문 전용 템플릿이 만든 Producer를 닫는다(빈이 아니므로 직접 닫는다). Boot 빈은 Boot가 닫는다.
     */
    @Override
    public void destroy() {
        kafkaTemplate.destroy();
    }

    /**
     * 원인 예외의 종류 · 메시지만 담는다(Kafka 예외 메시지에는 토픽 · 시간이 있고 본문은 없다).
     */
    private static IllegalStateException failed(Throwable cause) {
        return new IllegalStateException("Kafka 전송 실패: " + cause.getClass().getSimpleName() + ": "
            + cause.getMessage(), cause);
    }

    /**
     * 원인 사슬에서 처음 나오는 Kafka 클라이언트 예외. 그 안쪽 원인(예: UnknownTopicOrPartitionException)보다
     * 무엇을 얼마나 기다렸는지가 메시지에 있다. 없으면 받은 예외 그대로.
     */
    private static Throwable kafkaClientCause(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause.getClass().getName().startsWith("org.apache.kafka.")) {
                return cause;
            }
        }
        return error;
    }
}
