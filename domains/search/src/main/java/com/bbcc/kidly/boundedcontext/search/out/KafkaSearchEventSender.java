package com.bbcc.kidly.boundedcontext.search.out;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.bbcc.kidly.boundedcontext.search.app.port.SearchEventSender;
import com.bbcc.kidly.shared.event.DomainEvent;
import com.bbcc.kidly.shared.search.event.SearchPerformedEvent;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * 검색 이벤트를 kidly.search.events로 보낸다. 키는 correlationId(회원 ID)라 한 회원의 검색은 한 파티션에 순서대로 들어간다.
 * 본문은 공통 칸 7개 + data다. record를 그대로 바꾸면 record 필드가 아닌 칸(eventType · producer 등)이 빠진다.
 * 검색은 DB가 없어 아웃박스 없이 응답 스레드에서 넘겨받아 바로 보낸다. 전송은 검색 전용 스레드 하나에서 해서,
 * 브로커가 죽어 send가 max.block.ms만큼 멈춰도 검색 응답은 기다리지 않는다. 보내지 못한 이벤트는 로그만 남기고 버린다.
 */
@Slf4j
public class KafkaSearchEventSender implements SearchEventSender, DisposableBean {

    public static final String TOPIC = "kidly.search.events";

    // 앱이 내려갈 때 남은 전송을 기다리는 시간. 브로커가 죽어 있으면 하나에 max.block.ms가 걸리므로 다 못 보낼 수 있다
    private static final Duration DRAIN_TIMEOUT = Duration.ofSeconds(10);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final JsonMapper jsonMapper;
    private final ExecutorService executor;

    public KafkaSearchEventSender(KafkaTemplate<String, String> kafkaTemplate, JsonMapper jsonMapper,
        ExecutorService executor) {
        this.kafkaTemplate = kafkaTemplate;
        this.jsonMapper = jsonMapper;
        this.executor = executor;
    }

    @Override
    public void send(SearchPerformedEvent event) {
        try {
            var record = toRecord(event);
            executor.execute(() -> sendNow(event, record));
        } catch (RuntimeException e) {
            // 대기열이 가득 찼거나 앱이 내려가는 중이다. 검색 응답은 실패시키지 않는다
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
        var record = new ProducerRecord<>(TOPIC, event.correlationId(), toBody(event));
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

    // 본문(검색어)은 남기지 않는다
    private void logFailure(DomainEvent event, Throwable e) {
        // KafkaTemplate은 비동기 실패를 KafkaProducerException으로 감싸므로 가장 안쪽 원인의 종류를 남긴다.
        // 원인 메시지에는 직렬화하던 값(검색어)이 들어갈 수 있어 남기지 않는다
        var cause = NestedExceptionUtils.getMostSpecificCause(e).getClass().getName();
        log.error("이벤트를 Kafka로 보내지 못했습니다. eventType={} eventId={} key={} exception={}",
            event.eventType(), event.eventId(), event.correlationId(), cause);
    }

    // 남은 전송을 기다린 뒤 검색 전용 템플릿을 닫는다(빈이 아니므로 직접 닫는다). 못 보낸 건 ERROR로 남긴다
    @Override
    public void destroy() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(DRAIN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                logDropped(executor.shutdownNow());
            }
        } catch (InterruptedException e) {
            logDropped(executor.shutdownNow());
            Thread.currentThread().interrupt();
        }
        kafkaTemplate.destroy();
    }

    private static void logDropped(List<Runnable> dropped) {
        log.error("앱 종료로 Kafka에 보내지 못한 검색 이벤트가 있습니다. count={}", dropped.size());
    }
}
