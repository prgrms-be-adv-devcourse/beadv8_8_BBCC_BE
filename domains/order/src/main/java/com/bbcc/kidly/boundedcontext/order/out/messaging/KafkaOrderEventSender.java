package com.bbcc.kidly.boundedcontext.order.out.messaging;

import com.bbcc.kidly.boundedcontext.order.app.port.OrderEventSender;
import com.bbcc.kidly.boundedcontext.order.domain.outbox.OrderOutboxMessage;
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
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.stereotype.Component;

/**
 * 아웃박스 메시지를 Kafka로 보낸다(#540). 헤더 eventType · eventId를 붙이고 브로커가 받았다고 답할 때까지 기다린다.
 * Boot가 만든 ProducerFactory(연결 정보 · 보안 · 테스트 컨테이너 연결 포함)를 설정만 덮어 복사한 주문 전용 팩토리를 쓴다.
 * 복사본과 KafkaTemplate은 빈이 아니라 이 객체 안에만 있어, 다른 도메인은 Boot 기본 설정을 그대로 쓴다(spring.kafka.*도 그대로).
 * 보내기 한 번은 최대 SEND_TIMEOUT까지 기다린다: 릴레이는 스케줄러 스레드(1개) · DB 트랜잭션을 쥔 채 보내므로 오래 막히지 않게 한다.
 */
@Component
class KafkaOrderEventSender implements OrderEventSender, DisposableBean {

    private static final int MAX_BLOCK_MS = 5_000;
    private static final int DELIVERY_TIMEOUT_MS = 10_000;

    /**
     * 메타데이터 · 버퍼 대기(max.block) + 전달 제한(delivery.timeout). 이 안에 끝나지 않으면 실패로 본다.
     */
    static final Duration SEND_TIMEOUT = Duration.ofMillis(MAX_BLOCK_MS + DELIVERY_TIMEOUT_MS);

    /**
     * acks=all · 멱등: 브로커가 받은 메시지는 잃지 않고, Producer 재시도로 중복되지 않는다.
     * delivery.timeout은 request.timeout(+ linger 0) 이상이어야 한다.
     */
    private static final Map<String, Object> PRODUCER_OVERRIDES = Map.of(
        ProducerConfig.ACKS_CONFIG, "all",
        ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true,
        ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
        ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
        ProducerConfig.MAX_BLOCK_MS_CONFIG, MAX_BLOCK_MS,
        ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 5_000,
        ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, DELIVERY_TIMEOUT_MS);

    private final ProducerFactory<String, String> producerFactory;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @SuppressWarnings("unchecked")
    KafkaOrderEventSender(ProducerFactory<?, ?> bootProducerFactory) {
        this.producerFactory = (ProducerFactory<String, String>) bootProducerFactory
            .copyWithConfigurationOverride(PRODUCER_OVERRIDES);
        this.kafkaTemplate = new KafkaTemplate<>(producerFactory);
    }

    @Override
    public void send(OrderOutboxMessage message) {
        ProducerRecord<String, String> record = new ProducerRecord<>(message.getTopic(), message.getMessageKey(),
            message.getPayload());
        record.headers().add("eventType", message.getEventType().getBytes(StandardCharsets.UTF_8));
        record.headers().add("eventId", message.getEventId().toString().getBytes(StandardCharsets.UTF_8));
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

    @Override
    public void destroy() {
        if (producerFactory instanceof DisposableBean disposable) {
            try {
                disposable.destroy();
            } catch (Exception e) {
                throw new IllegalStateException("주문 Kafka Producer를 닫지 못했습니다", e);
            }
        }
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
