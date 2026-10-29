package com.bbcc.kidly;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.support.IntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.errors.TimeoutException;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.kafka.KafkaContainer;

/**
 * 테스트용 Kafka(TestcontainersConfiguration)가 뜨고, 운영처럼 토픽 자동 생성이 꺼져 있는지(#538).
 * 토픽은 보내는 쪽이 선언해야 하므로, 선언하지 않은 토픽으로 보내면 메타데이터를 못 찾아 실패해야 한다.
 * 아직 spring-kafka(KafkaTemplate)가 없어 kafka-clients로 Producer · Admin을 직접 만든다.
 */
@IntegrationTest
class TestKafkaIntegrationTest {

    // 선언하지 않은 토픽은 메타데이터가 오지 않으므로 기다리는 시간을 짧게 둔다(테스트 시간을 늘리지 않게)
    private static final int MAX_BLOCK_MS = 3_000;

    @Autowired
    private KafkaContainer kafka;

    @Test
    @DisplayName("선언하지 않은 토픽으로 보내면 토픽이 자동으로 생기지 않아 메타데이터 대기 시간 초과로 실패한다")
    void undeclaredTopicIsNotCreated() {
        String topic = "undeclared-" + UUID.randomUUID();

        try (Producer<String, String> producer = producer()) {
            assertThatThrownBy(() -> producer.send(new ProducerRecord<>(topic, "key", "value"))
                .get(MAX_BLOCK_MS * 2L, TimeUnit.MILLISECONDS))
                .isInstanceOf(ExecutionException.class)
                .cause()
                .isInstanceOf(TimeoutException.class)
                .hasMessageContaining("not present in metadata");
        }
    }

    @Test
    @DisplayName("대조군: Admin으로 선언한 토픽에는 보내기가 성공한다")
    void declaredTopicAcceptsMessages() throws Exception {
        String topic = "declared-" + UUID.randomUUID();
        try (Admin admin = Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG,
            kafka.getBootstrapServers()))) {
            admin.createTopics(List.of(new NewTopic(topic, 1, (short) 1))).all().get(10, TimeUnit.SECONDS);
        }

        try (Producer<String, String> producer = producer()) {
            RecordMetadata sent = producer.send(new ProducerRecord<>(topic, "key", "value"))
                .get(10, TimeUnit.SECONDS);

            assertThat(sent.topic()).isEqualTo(topic);
            assertThat(sent.offset()).isZero();
        }
    }

    private Producer<String, String> producer() {
        return new KafkaProducer<>(Map.of(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
            ProducerConfig.MAX_BLOCK_MS_CONFIG, MAX_BLOCK_MS,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class));
    }
}
