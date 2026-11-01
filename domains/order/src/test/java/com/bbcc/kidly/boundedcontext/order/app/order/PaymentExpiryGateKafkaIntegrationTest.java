package com.bbcc.kidly.boundedcontext.order.app.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.order.app.port.PaymentEventLag;
import com.bbcc.kidly.boundedcontext.support.KafkaIntegrationTest;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 * 결제 마감 만료 실행 조건(#594)의 "스위치 켜짐" 경로를 실제 브로커로 본다. 테스트 설정은 스위치가 꺼져 있으므로 컨텍스트 설정을
 * 바꾸지 않고, 스위치 켜짐 값으로 게이트를 직접 만들어 앱의 실제 대기량 확인(PaymentEventLag 빈)을 쓴다.
 * 결제 리스너는 이 테스트에서 켜지 않는다(멈춰 있어도 늘 확인하는지 = 리스너 상태와 상관없음). 그룹 커밋 위치는 테스트가 놓는다.
 */
@KafkaIntegrationTest
class PaymentExpiryGateKafkaIntegrationTest {

    private static final String TOPIC = "kidly.payment.events";
    private static final String GROUP = "kidly.order";

    @Autowired
    private KafkaContainer kafka;

    @Autowired
    private PaymentEventLag paymentEventLag;

    private Admin admin;
    private PaymentExpiryGate gate;

    @BeforeEach
    void setUp() {
        admin = Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers()));
        gate = new PaymentExpiryGate(new OrderKafkaConsumerProperties(true), paymentEventLag,
            Clock.systemDefaultZone());
    }

    @AfterEach
    void tearDown() {
        admin.close();
    }

    @Test
    @DisplayName("스위치 켜짐: 주문 그룹이 결제 토픽 끝까지 처리했으면 만료를 실행한다")
    void runsWhenCaughtUp() throws Exception {
        commit(endOffsets());

        assertThat(gate.allowsRun()).isTrue();
    }

    @Test
    @DisplayName("스위치 켜짐: 처리하지 않은 결제 이벤트가 있으면(결제 리스너가 멈춰 있어도) 만료를 건너뛴다")
    void skipsWhenLagging() throws Exception {
        commit(endOffsets());
        send(0);

        assertThat(gate.allowsRun()).isFalse();

        commit(endOffsets());
        assertThat(gate.allowsRun()).as("따라잡으면 다시 실행").isTrue();
    }

    private Map<TopicPartition, Long> endOffsets() throws Exception {
        List<TopicPartition> partitions = admin.describeTopics(List.of(TOPIC)).allTopicNames()
            .get(10, TimeUnit.SECONDS).get(TOPIC).partitions().stream()
            .map(info -> new TopicPartition(TOPIC, info.partition()))
            .toList();
        Map<TopicPartition, OffsetSpec> latest = partitions.stream()
            .collect(Collectors.toMap(partition -> partition, partition -> OffsetSpec.latest()));
        Map<TopicPartition, Long> ends = new HashMap<>();
        admin.listOffsets(latest).all().get(10, TimeUnit.SECONDS)
            .forEach((partition, info) -> ends.put(partition, info.offset()));
        return ends;
    }

    // 주문 그룹에 활성 멤버가 없을 때(결제 리스너 멈춤) 커밋 위치를 직접 놓는다
    private void commit(Map<TopicPartition, Long> offsets) throws Exception {
        Map<TopicPartition, OffsetAndMetadata> committed = new HashMap<>();
        offsets.forEach((partition, offset) -> committed.put(partition, new OffsetAndMetadata(offset)));
        admin.alterConsumerGroupOffsets(GROUP, committed).all().get(10, TimeUnit.SECONDS);
    }

    private void send(int partition) throws Exception {
        try (var producer = new KafkaProducer<String, String>(Map.of(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class))) {
            producer.send(new ProducerRecord<>(TOPIC, partition, "1", "{}")).get(10, TimeUnit.SECONDS);
        }
    }
}
