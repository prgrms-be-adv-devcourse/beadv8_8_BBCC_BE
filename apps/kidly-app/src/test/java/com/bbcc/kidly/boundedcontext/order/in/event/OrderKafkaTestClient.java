package com.bbcc.kidly.boundedcontext.order.in.event;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.bbcc.kidly.shared.event.DomainEvent;
import com.bbcc.kidly.support.AsyncWait;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.json.JsonMapper;

/**
 * 주문 Kafka 수신 테스트(#541)가 쓰는 도구: 보내는 쪽 규칙대로 메시지를 넣고(키 · 헤더 2개 · 본문 7칸 + data),
 * 주문 리스너를 그 테스트 안에서만 켜고 끄며(OrderKafkaListenerTestGuard에서 꺼냄), 그룹의 처리 완료 위치 · DLT를 읽는다. 빈이 아니라 테스트가 직접 만든다.
 * 리스너는 처음 읽는 그룹을 earliest로 읽으므로, 켠 뒤 다른 테스트가 넣어 둔 메시지를 모두 처리할 때까지(lag 0) 기다린다.
 */
final class OrderKafkaTestClient {

    static final String GROUP = "kidly.order";
    static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final KafkaContainer kafka;
    private final JsonMapper jsonMapper;

    OrderKafkaTestClient(KafkaContainer kafka, JsonMapper jsonMapper) {
        this.kafka = kafka;
        this.jsonMapper = jsonMapper;
    }

    /**
     * 리스너를 켜고, 파티션을 배정받은 뒤 그 토픽에 쌓여 있던 메시지를 모두 처리할 때까지 기다린다.
     */
    MessageListenerContainer start(OrderKafkaListenerTestGuard guard, String listenerId, String topic) {
        MessageListenerContainer listener = guard.listener(listenerId);
        listener.start();
        AsyncWait.until(TIMEOUT, () -> !listener.getAssignedPartitions().isEmpty());
        Map<TopicPartition, Long> ends = endOffsets(topic);
        AsyncWait.until(TIMEOUT, () -> ends.entrySet().stream()
            .allMatch(end -> end.getValue() == 0 || committedOffset(end.getKey()) >= end.getValue()));
        return listener;
    }

    /**
     * 보내는 쪽 규칙대로: 키 correlationId, 헤더 eventType · eventId, 본문 공통 칸 7개 + data.
     */
    RecordMetadata send(String topic, DomainEvent event) {
        return send(topic, event.correlationId(), event.eventType(), event.eventId().toString(), envelope(event));
    }

    RecordMetadata send(String topic, String key, String eventType, String eventId, String body) {
        var record = new ProducerRecord<>(topic, key, body);
        if (eventType != null) {
            record.headers().add("eventType", eventType.getBytes(UTF_8));
        }
        record.headers().add("eventId", eventId.getBytes(UTF_8));
        try (var producer = new KafkaProducer<String, String>(Map.of(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class))) {
            return producer.send(record).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    Map<String, Object> envelopeFields(DomainEvent event) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventId", event.eventId());
        body.put("eventType", event.eventType());
        body.put("schemaVersion", event.schemaVersion());
        body.put("occurredAt", event.occurredAt());
        body.put("producer", event.producer());
        body.put("aggregateId", event.aggregateId());
        body.put("correlationId", event.correlationId());
        body.put("data", event.data());
        return body;
    }

    String envelope(DomainEvent event) {
        return toJson(envelopeFields(event));
    }

    String toJson(Object value) {
        return jsonMapper.writeValueAsString(value);
    }

    /**
     * 그룹의 처리 완료 위치가 이 메시지를 지날 때까지 기다린다(처리 · 커밋 뒤 리스너까지 끝남).
     */
    void awaitProcessed(RecordMetadata sent) {
        var partition = new TopicPartition(sent.topic(), sent.partition());
        AsyncWait.until(TIMEOUT, () -> committedOffset(partition) > sent.offset());
    }

    /**
     * 그 토픽의 주문 DLT에서 이 eventId 헤더를 단 메시지(다른 테스트의 DLT 메시지는 뺀다).
     */
    List<ConsumerRecord<String, String>> deadLetters(String topic, String eventId) {
        return readAll(topic + ".order.dlt").stream()
            .filter(record -> eventId.equals(header(record, "eventId")))
            .toList();
    }

    static String newEventId() {
        return UUID.randomUUID().toString();
    }

    static String header(ConsumerRecord<?, ?> record, String name) {
        var header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), UTF_8);
    }

    private long committedOffset(TopicPartition partition) {
        try (var admin = admin()) {
            OffsetAndMetadata committed = admin.listConsumerGroupOffsets(GROUP).partitionsToOffsetAndMetadata()
                .get(10, TimeUnit.SECONDS).get(partition);
            return committed == null ? -1 : committed.offset();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private Map<TopicPartition, Long> endOffsets(String topic) {
        try (var admin = admin()) {
            var partitions = admin.describeTopics(List.of(topic)).allTopicNames().get(10, TimeUnit.SECONDS)
                .get(topic).partitions().stream()
                .map(info -> new TopicPartition(topic, info.partition()))
                .toList();
            Map<TopicPartition, OffsetSpec> latest = new LinkedHashMap<>();
            partitions.forEach(partition -> latest.put(partition, OffsetSpec.latest()));
            Map<TopicPartition, Long> ends = new LinkedHashMap<>();
            admin.listOffsets(latest).all().get(10, TimeUnit.SECONDS)
                .forEach((partition, info) -> ends.put(partition, info.offset()));
            return ends;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private List<ConsumerRecord<String, String>> readAll(String topic) {
        try (var consumer = new KafkaConsumer<String, String>(Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class))) {
            var partitions = consumer.partitionsFor(topic).stream()
                .map(info -> new TopicPartition(topic, info.partition()))
                .toList();
            consumer.assign(partitions);
            consumer.seekToBeginning(partitions);
            var ends = consumer.endOffsets(partitions);
            var records = new ArrayList<ConsumerRecord<String, String>>();
            while (partitions.stream().anyMatch(partition -> consumer.position(partition) < ends.get(partition))) {
                consumer.poll(Duration.ofMillis(200)).forEach(records::add);
            }
            return records;
        }
    }

    private Admin admin() {
        return Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers()));
    }
}
