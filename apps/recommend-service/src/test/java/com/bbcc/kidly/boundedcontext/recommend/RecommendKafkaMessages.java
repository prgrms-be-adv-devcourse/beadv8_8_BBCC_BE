package com.bbcc.kidly.boundedcontext.recommend;

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
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import tools.jackson.databind.json.JsonMapper;

/**
 * 추천의 Kafka 수신 테스트가 쓰는 도우미다. 추천 리스너를 켜고, 토픽에 메시지를 넣고,
 * 추천 그룹이 어디까지 처리했는지와 DLT에 무엇이 들어왔는지 읽는다.
 */
final class RecommendKafkaMessages {

    static final String GROUP = "kidly.recommend";
    static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final String bootstrapServers;
    private final JsonMapper jsonMapper;

    RecommendKafkaMessages(String bootstrapServers, JsonMapper jsonMapper) {
        this.bootstrapServers = bootstrapServers;
        this.jsonMapper = jsonMapper;
    }

    // 추천 그룹의 리스너만 켜고, 모두 파티션을 배정받을 때까지 기다린다.
    // 읽기 시작 위치가 latest라 파티션을 배정받기 전에 보낸 메시지는 받지 못한다
    static List<MessageListenerContainer> startListeners(KafkaListenerEndpointRegistry registry) {
        List<MessageListenerContainer> listeners = registry.getAllListenerContainers().stream()
            .filter(container -> GROUP.equals(container.getGroupId()))
            .toList();
        listeners.forEach(MessageListenerContainer::start);
        AsyncWait.until(TIMEOUT, () -> listeners.stream()
            .allMatch(container -> !container.getAssignedPartitions().isEmpty()));
        return listeners;
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
        record.headers().add("eventType", eventType.getBytes(UTF_8));
        record.headers().add("eventId", eventId.toString().getBytes(UTF_8));
        try (var producer = new KafkaProducer<String, String>(Map.of(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class))) {
            return producer.send(record).get(10, TimeUnit.SECONDS);
        }
    }

    // 값이 바뀌지 않는 메시지는 결과로 처리 여부를 알 수 없다.
    // 한 건씩 처리 완료를 커밋하므로, 추천 그룹의 커밋 위치가 그 메시지를 지나면 처리한 것이다
    void awaitProcessed(String topic, RecordMetadata sent) {
        var partition = new TopicPartition(topic, sent.partition());
        var config = Map.<String, Object>of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        try (var admin = Admin.create(config)) {
            AsyncWait.until(TIMEOUT, () -> committedOffset(admin, partition) > sent.offset());
        }
    }

    // DLT를 처음부터 지금 끝까지 읽어 그 키의 메시지만 돌려준다. 다른 테스트가 넣은 DLT 메시지도 있다. DLT가 아직 없으면 빈 목록이다
    List<ConsumerRecord<String, String>> deadLetters(String deadLetterTopic, String key) {
        try (var consumer = new KafkaConsumer<String, String>(Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class))) {
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

    private static long committedOffset(Admin admin, TopicPartition partition) {
        try {
            var committed = admin.listConsumerGroupOffsets(GROUP).partitionsToOffsetAndMetadata()
                .get(10, TimeUnit.SECONDS).get(partition);
            return committed == null ? -1 : committed.offset();
        } catch (Exception e) {
            throw new IllegalStateException("추천 그룹의 커밋 위치를 읽지 못했습니다", e);
        }
    }
}
