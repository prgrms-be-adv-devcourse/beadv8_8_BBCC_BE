package com.bbcc.kidly.boundedcontext.payment.out.kafka;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.bbcc.kidly.boundedcontext.payment.app.port.PaymentEventSender;
import com.bbcc.kidly.shared.event.DomainEvent;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * 결제 이벤트를 kidly.payment.events로 보낸다. 같은 키(주문 ID · 정산서 ID)의 이벤트는 한 파티션에 들어가 순서가 지켜진다.
 * 본문은 공통 칸 7개 + data다. record를 그대로 바꾸면 record 필드가 아닌 칸(eventType · producer 등)이 빠진다.
 * 전송은 결제 전용 스레드 하나에서 한다. 브로커가 죽어 send가 max.block.ms만큼 멈춰도 커밋 뒤 요청 스레드
 * (같은 커밋 뒤에 도는 주문 · 정산 리스너, API 응답)는 기다리지 않는다. 스레드가 하나라 받은 순서대로 보낸다.
 */
@Slf4j
public class KafkaPaymentEventSender implements PaymentEventSender, DisposableBean {

    static final String TOPIC = "kidly.payment.events";

    // 앱이 내려갈 때 남은 전송을 기다리는 시간. 브로커가 죽어 있으면 하나에 max.block.ms가 걸리므로 다 못 보낼 수 있다
    private static final Duration DRAIN_TIMEOUT = Duration.ofSeconds(10);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final JsonMapper jsonMapper;
    private final ExecutorService executor;

    public KafkaPaymentEventSender(KafkaTemplate<String, String> kafkaTemplate, JsonMapper jsonMapper,
        ExecutorService executor) {
        this.kafkaTemplate = kafkaTemplate;
        this.jsonMapper = jsonMapper;
        this.executor = executor;
    }

    @Override
    public void send(DomainEvent event) {
        try {
            ProducerRecord<String, String> record = toRecord(event);
            executor.execute(() -> sendNow(event, record));
        } catch (RuntimeException e) {
            // 대기열이 가득 찼거나 앱이 내려가는 중이다. 커밋 뒤 리스너에서 예외가 나가면 이미 커밋된 요청이 실패로 응답한다
            logFailure(event, e);
        }
    }

    private void sendNow(DomainEvent event, ProducerRecord<String, String> record) {
        try {
            kafkaTemplate.send(record)
                .whenComplete((result, e) -> {
                    if (e != null) {
                        logFailure(event, e);
                    }
                });
        } catch (RuntimeException e) {
            logFailure(event, e);
        }
    }

    private ProducerRecord<String, String> toRecord(DomainEvent event) {
        ProducerRecord<String, String> record = new ProducerRecord<>(TOPIC, event.correlationId(), toBody(event));
        record.headers()
            .add("eventType", event.eventType().getBytes(UTF_8))
            .add("eventId", event.eventId().toString().getBytes(UTF_8));
        return record;
    }

    private String toBody(DomainEvent event) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventId", event.eventId());
        body.put("eventType", event.eventType());
        body.put("schemaVersion", event.schemaVersion());
        body.put("occurredAt", event.occurredAt());
        body.put("producer", event.producer());
        body.put("aggregateId", event.aggregateId());
        body.put("correlationId", event.correlationId());
        body.put("data", event.data());
        return jsonMapper.writeValueAsString(body);
    }

    // 본문(회원 ID · 금액 등)은 남기지 않는다
    private void logFailure(DomainEvent event, Throwable e) {
        log.error("이벤트를 Kafka로 보내지 못했습니다. eventType={} eventId={} key={} reason={}",
            event.eventType(), event.eventId(), event.correlationId(), e.getMessage());
    }

    // 남은 전송을 기다린 뒤 결제 전용 템플릿을 닫는다(빈이 아니므로 직접 닫는다). 못 보낸 건 ERROR로 남긴다
    @Override
    public void destroy() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(DRAIN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                List<Runnable> dropped = executor.shutdownNow();
                log.error("앱 종료로 Kafka에 보내지 못한 결제 이벤트가 있습니다. count={}", dropped.size());
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        kafkaTemplate.destroy();
    }
}
