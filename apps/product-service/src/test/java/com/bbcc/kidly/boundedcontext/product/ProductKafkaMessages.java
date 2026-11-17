package com.bbcc.kidly.boundedcontext.product;

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
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.kafka.listener.MessageListenerContainer;
import tools.jackson.databind.json.JsonMapper;

final class ProductKafkaMessages {

    static final String GROUP = "kidly.product";
    static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final String bootstrapServers;
    private final JsonMapper jsonMapper;

    ProductKafkaMessages(String bootstrapServers, JsonMapper jsonMapper) {
        this.bootstrapServers = bootstrapServers;
        this.jsonMapper = jsonMapper;
    }

    static MessageListenerContainer startListener(ProductKafkaListenerTestGuard guard, String id) {
        MessageListenerContainer listener = guard.listener(id);
        listener.start();
        AsyncWait.until(TIMEOUT, () -> !listener.getAssignedPartitions().isEmpty());
        return listener;
    }

    RecordMetadata send(String topic, DomainEvent event) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventId", event.eventId());
        body.put("eventType", event.eventType());
        body.put("schemaVersion", event.schemaVersion());
        body.put("occurredAt", event.occurredAt());
        body.put("producer", event.producer());
        body.put("aggregateId", event.aggregateId());
        body.put("correlationId", event.correlationId());
        body.put("data", event.data());
        return sendRaw(topic, event.correlationId(), event.eventType(), event.eventId(),
            jsonMapper.writeValueAsString(body));
    }

    RecordMetadata sendRaw(String topic, String key, String eventType, UUID eventId, String body) throws Exception {
        var record = new ProducerRecord<>(topic, key, body);
        if (eventType != null) {
            record.headers().add("eventType", eventType.getBytes(UTF_8));
        }
        record.headers().add("eventId", eventId.toString().getBytes(UTF_8));
        try (var producer = new KafkaProducer<String, String>(Map.of(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class))) {
            return producer.send(record).get(10, TimeUnit.SECONDS);
        }
    }

    void awaitProcessed(String topic, RecordMetadata sent) {
        var partition = new TopicPartition(topic, sent.partition());
        var config = Map.<String, Object>of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        try (var admin = Admin.create(config)) {
            AsyncWait.until(TIMEOUT, () -> committedOffset(admin, partition) > sent.offset());
        }
    }

    void awaitCaughtUp(String topic) {
        var config = Map.<String, Object>of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        try (var admin = Admin.create(config); var consumer = newConsumer()) {
            var partitions = consumer.partitionsFor(topic).stream()
                .map(info -> new TopicPartition(topic, info.partition()))
                .toList();
            Map<TopicPartition, Long> ends = consumer.endOffsets(partitions);
            AsyncWait.until(TIMEOUT, () -> ends.entrySet().stream().allMatch(end -> {
                long committed = committedOffset(admin, end.getKey());
                return committed == -1 || committed >= end.getValue();
            }));
        }
    }

    List<ConsumerRecord<String, String>> deadLetters(String deadLetterTopic, String key) {
        try (var consumer = newConsumer()) {
            if (consumer.partitionsFor(deadLetterTopic).isEmpty()) {
                return List.of();
            }
            var partition = new TopicPartition(deadLetterTopic, 0);
            consumer.assign(List.of(partition));
            consumer.seekToBeginning(List.of(partition));
            var end = consumer.endOffsets(List.of(partition)).get(partition);
            var records = new ArrayList<ConsumerRecord<String, String>>();
            while (consumer.position(partition) < end) {
                consumer.poll(Duration.ofMillis(200)).forEach(records::add);
            }
            return records.stream().filter(record -> key.equals(record.key())).toList();
        }
    }

    private KafkaConsumer<String, String> newConsumer() {
        return new KafkaConsumer<>(Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class));
    }

    private static long committedOffset(Admin admin, TopicPartition partition) {
        try {
            var committed = admin.listConsumerGroupOffsets(GROUP).partitionsToOffsetAndMetadata()
                .get(10, TimeUnit.SECONDS).get(partition);
            return committed == null ? -1 : committed.offset();
        } catch (Exception e) {
            throw new IllegalStateException("상품 그룹의 커밋 위치를 읽지 못했습니다", e);
        }
    }
}
