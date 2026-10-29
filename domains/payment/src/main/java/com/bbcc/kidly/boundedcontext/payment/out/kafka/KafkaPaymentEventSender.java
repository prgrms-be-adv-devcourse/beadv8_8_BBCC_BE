package com.bbcc.kidly.boundedcontext.payment.out.kafka;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.bbcc.kidly.boundedcontext.payment.app.port.PaymentEventSender;
import com.bbcc.kidly.shared.event.DomainEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * 결제 이벤트를 kidly.payment.events로 보낸다. 같은 키(주문 ID · 정산서 ID)의 이벤트는 한 파티션에 들어가 순서가 지켜진다.
 * 본문은 공통 칸 7개 + data다. record를 그대로 바꾸면 record 필드가 아닌 칸(eventType · producer 등)이 빠진다.
 */
@Slf4j
public class KafkaPaymentEventSender implements PaymentEventSender, DisposableBean {

    static final String TOPIC = "kidly.payment.events";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final JsonMapper jsonMapper;

    public KafkaPaymentEventSender(KafkaTemplate<String, String> kafkaTemplate, JsonMapper jsonMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void send(DomainEvent event) {
        try {
            kafkaTemplate.send(toRecord(event))
                .whenComplete((result, e) -> {
                    if (e != null) {
                        logFailure(event, e);
                    }
                });
        } catch (RuntimeException e) {
            // 커밋 뒤 리스너에서 예외가 나가면 이미 커밋된 요청이 실패로 응답한다
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

    // 결제 전용 템플릿은 빈이 아니므로 앱이 내려갈 때 프로듀서를 직접 닫는다
    @Override
    public void destroy() {
        kafkaTemplate.destroy();
    }
}
