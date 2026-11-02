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

/**
 * 상품의 Kafka 수신 테스트가 쓰는 도우미다. 상품 리스너를 켜고, 토픽에 메시지를 넣고,
 * 상품 그룹이 어디까지 처리했는지와 DLT에 무엇이 들어왔는지 읽는다.
 */
final class ProductKafkaMessages {

    static final String GROUP = "kidly.product";
    static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final String bootstrapServers;
    private final JsonMapper jsonMapper;

    ProductKafkaMessages(String bootstrapServers, JsonMapper jsonMapper) {
        this.bootstrapServers = bootstrapServers;
        this.jsonMapper = jsonMapper;
    }

    // 가드가 들고 있는 상품 리스너 중 그 테스트가 보는 리스너 하나만 켜고, 파티션을 배정받을 때까지 기다린다.
    // 읽기 시작 위치가 latest라 파티션을 배정받기 전에 보낸 메시지는 받지 못한다
    static MessageListenerContainer startListener(ProductKafkaListenerTestGuard guard, String id) {
        MessageListenerContainer listener = guard.listener(id);
        listener.start();
        AsyncWait.until(TIMEOUT, () -> !listener.getAssignedPartitions().isEmpty());
        return listener;
    }

    // 보내는 도메인과 같은 형식으로 넣는다. 키는 correlationId, 헤더는 eventType · eventId, 본문은 공통 칸 7개 + data다
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

    // 보내는 도메인이 보내지 않는 메시지(다른 종류 · 깨진 본문)를 그대로 넣는다
    RecordMetadata sendRaw(String topic, String key, String eventType, UUID eventId, String body) throws Exception {
        var record = new ProducerRecord<>(topic, key, body);
        // eventType 헤더가 없는 메시지도 넣을 수 있게, null이면 헤더를 붙이지 않는다
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

    // 값이 바뀌지 않는 메시지는 결과로 처리 여부를 알 수 없다.
    // 한 건씩 처리 완료를 커밋하므로, 상품 그룹의 커밋 위치가 그 메시지를 지나면 처리한 것이다
    void awaitProcessed(String topic, RecordMetadata sent) {
        var partition = new TopicPartition(topic, sent.partition());
        var config = Map.<String, Object>of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        try (var admin = Admin.create(config)) {
            AsyncWait.until(TIMEOUT, () -> committedOffset(admin, partition) > sent.offset());
        }
    }

    // 지금 토픽 끝까지 들어온 메시지를 상품 그룹이 모두 처리할 때까지 기다린다. 커밋 위치가 없는 파티션은
    // 배정받을 때 끝에서 읽기 시작했으므로 처리할 메시지가 없다
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

    // DLT를 처음부터 지금 끝까지 읽어 그 키의 메시지만 돌려준다. 다른 테스트가 넣은 DLT 메시지도 있다. DLT가 아직 없으면 빈 목록이다
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
