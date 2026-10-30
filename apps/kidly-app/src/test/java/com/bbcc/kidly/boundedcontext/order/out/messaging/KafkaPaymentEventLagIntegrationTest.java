package com.bbcc.kidly.boundedcontext.order.out.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.order.app.port.PaymentEventLag;
import com.bbcc.kidly.boundedcontext.order.domain.expiry.PaymentEventLagCheck;
import com.bbcc.kidly.support.KafkaIntegrationTest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.kafka.KafkaContainer;

/**
 * 주문(kidly.order)의 결제 이벤트(kidly.payment.events) 처리 대기량 확인(#561)을 실제 브로커로 본다.
 * 주문은 아직 결제 이벤트를 Kafka로 받지 않으므로(그 그룹에 활성 멤버가 없음) 테스트가 그룹의 커밋 위치를 직접 놓는다.
 * 토픽에는 다른 테스트가 넣은 결제 메시지도 있으므로 지금 끝 오프셋을 기준으로 맞춘다.
 */
@KafkaIntegrationTest
class KafkaPaymentEventLagIntegrationTest {

    private static final String TOPIC = "kidly.payment.events";
    private static final String GROUP = "kidly.order";

    @Autowired
    private KafkaContainer kafka;

    @Autowired
    private PaymentEventLag paymentEventLag;

    private Admin admin;

    @BeforeEach
    void setUp() {
        admin = Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers()));
    }

    // 다음 테스트가 커밋 없는 상태에서 시작하게 그룹의 결제 토픽 커밋을 지운다
    @AfterEach
    void tearDown() throws Exception {
        var committed = admin.listConsumerGroupOffsets(GROUP).partitionsToOffsetAndMetadata().get(10, TimeUnit.SECONDS)
            .keySet().stream().filter(partition -> partition.topic().equals(TOPIC)).collect(Collectors.toSet());
        if (!committed.isEmpty()) {
            admin.deleteConsumerGroupOffsets(GROUP, committed).all().get(10, TimeUnit.SECONDS);
        }
        admin.close();
    }

    @Test
    @DisplayName("주문 그룹의 커밋 위치가 모든 파티션의 끝이면 대기 0")
    void caughtUp() throws Exception {
        commit(endOffsets());

        assertThat(paymentEventLag.check()).isEqualTo(PaymentEventLagCheck.caughtUp());
    }

    @Test
    @DisplayName("끝까지 처리한 뒤 결제 이벤트가 2건 더 쌓이면 대기 2")
    void lagging() throws Exception {
        commit(endOffsets());

        send(0);
        send(1);

        assertThat(paymentEventLag.check()).isEqualTo(PaymentEventLagCheck.lagging(2));
    }

    @Test
    @DisplayName("읽을 메시지가 있는 파티션에 주문 그룹의 커밋이 없으면 배정 · 첫 처리 전")
    void notAssigned() throws Exception {
        send(0);
        Map<TopicPartition, Long> ends = endOffsets();
        ends.remove(new TopicPartition(TOPIC, 0));
        commit(ends);

        assertThat(paymentEventLag.check()).isEqualTo(PaymentEventLagCheck.notAssigned());
    }

    private Map<TopicPartition, Long> endOffsets() throws Exception {
        Set<TopicPartition> partitions = admin.describeTopics(List.of(TOPIC)).allTopicNames()
            .get(10, TimeUnit.SECONDS).get(TOPIC).partitions().stream()
            .map(info -> new TopicPartition(TOPIC, info.partition()))
            .collect(Collectors.toSet());
        Map<TopicPartition, OffsetSpec> latest = new HashMap<>();
        partitions.forEach(partition -> latest.put(partition, OffsetSpec.latest()));
        Map<TopicPartition, Long> ends = new HashMap<>();
        admin.listOffsets(latest).all().get(10, TimeUnit.SECONDS)
            .forEach((partition, info) -> ends.put(partition, info.offset()));
        return ends;
    }

    private void commit(Map<TopicPartition, Long> offsets) throws Exception {
        Map<TopicPartition, OffsetAndMetadata> committed = new HashMap<>();
        offsets.forEach((partition, offset) -> committed.put(partition, new OffsetAndMetadata(offset)));
        admin.alterConsumerGroupOffsets(GROUP, committed).all().get(10, TimeUnit.SECONDS);
    }

    // 결제가 보내는 것과 같은 토픽에 파티션을 정해 한 건 넣는다(대기량 계산은 본문을 읽지 않는다)
    private void send(int partition) throws Exception {
        try (var producer = new KafkaProducer<String, String>(Map.of(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class))) {
            producer.send(new ProducerRecord<>(TOPIC, partition, "1", "{}")).get(10, TimeUnit.SECONDS);
        }
    }
}
