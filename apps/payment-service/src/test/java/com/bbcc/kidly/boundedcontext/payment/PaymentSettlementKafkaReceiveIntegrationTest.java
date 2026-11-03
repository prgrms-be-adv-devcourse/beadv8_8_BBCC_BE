package com.bbcc.kidly.boundedcontext.payment;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.shared.event.DomainEvent;
import com.bbcc.kidly.shared.settlement.event.SettlementPayoutRequestedEvent;
import com.bbcc.kidly.support.AsyncWait;
import com.bbcc.kidly.support.KafkaIntegrationTest;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.json.JsonMapper;

/**
 * 정산이 Kafka로 보내기 시작했을 때 결제가 지급 요청을 받아 입금하는지 미리 본다(#607).
 * 정산은 아직 kidly.settlement.events를 선언하지 않으므로 테스트가 만든다. 결제의 정산 리스너는 기본으로 꺼져 있어 여기서만 켠다.
 * 메시지는 팀 규칙의 모양(키 = 정산서 ID, 헤더 eventType · eventId, 본문 = 공통 칸 7개 + data)으로 직접 넣는다.
 */
@KafkaIntegrationTest
class PaymentSettlementKafkaReceiveIntegrationTest {

    private static final String SETTLEMENT_TOPIC = "kidly.settlement.events";
    private static final String SETTLEMENT_DLT = "kidly.settlement.events.payment.dlt";
    private static final String GROUP = "kidly.payment";
    private static final String LISTENER_ID = "paymentSettlementEventListener";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    // 재시도는 1초 간격 3번이라, 재시도했다면 DLT에 들어가기까지 3초 이상 걸린다
    private static final long RETRY_BACKOFF_TOTAL_MS = 3_000;

    @Autowired
    private KafkaListenerEndpointRegistry registry;

    @Autowired
    private KafkaContainer kafka;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final long sellerId = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
    private final long settlementId = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);

    private MessageListenerContainer listener;

    @BeforeEach
    void startListener() {
        createSettlementTopic();
        listener = registry.getListenerContainer(LISTENER_ID);
        listener.start();
        AsyncWait.until(TIMEOUT, () -> !listener.getAssignedPartitions().isEmpty());
    }

    // stop()은 처리 중인 메시지가 끝날 때까지 기다리므로 뒤이은 테이블 비우기와 겹치지 않는다
    @AfterEach
    void stopListener() {
        listener.stop();
    }

    @Test
    @DisplayName("settlement.payout-requested를 받으면 판매자 예치금에 입금하고, 같은 메시지를 두 번 받아도 한 번만 입금한다")
    void payoutOnce() throws Exception {
        String body = envelope(payoutRequested(270_000L));

        send("settlement.payout-requested", body);
        RecordMetadata second = send("settlement.payout-requested", body);

        awaitProcessed(second);
        assertThat(balance()).isEqualTo(270_000L);
        assertThat(jdbcTemplate.queryForList("select status from payment.payout where settlement_id = ?",
            String.class, settlementId)).containsExactly("COMPLETED");
    }

    @Test
    @DisplayName("결제가 받지 않는 정산 종류는 건너뛰고 DLT로 보내지 않는다")
    void skipOtherEventType() throws Exception {
        send("settlement.closed", "결제가 받지 않는 종류라 읽지 않는 본문");
        // 키(정산서 ID)가 같아 같은 파티션이므로, 뒤 메시지를 처리했으면 앞 메시지는 이미 처리됐다
        send("settlement.payout-requested", envelope(payoutRequested(10_000L)));

        AsyncWait.until(TIMEOUT, () -> Long.valueOf(10_000L).equals(balance()));
        assertThat(deadLetters()).isEmpty();
    }

    @Test
    @DisplayName("본문을 읽지 못하면 재시도 없이 DLT로 보낸다")
    void deadLetterUnreadableBody() throws Exception {
        String body = "{\"data\":{\"settlementId\":\"숫자 아님\"}}";

        RecordMetadata sent = send("settlement.payout-requested", body);

        AsyncWait.until(TIMEOUT, () -> !deadLetters().isEmpty());
        assertThat(deadLetters()).singleElement().satisfies(deadLetter -> {
            assertThat(deadLetter.value()).isEqualTo(body);
            assertThat(deadLetter.timestamp() - sent.timestamp()).isLessThan(RETRY_BACKOFF_TOTAL_MS);
        });
        assertThat(balance()).isNull();
    }

    private SettlementPayoutRequestedEvent payoutRequested(long amount) {
        return new SettlementPayoutRequestedEvent(UUID.randomUUID(), LocalDateTime.now(),
            new SettlementPayoutRequestedEvent.Data(settlementId, sellerId, amount, LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 31)));
    }

    private Long balance() {
        List<Long> balances = jdbcTemplate.queryForList(
            "select balance from payment.wallet where member_id = ?", Long.class, sellerId);
        return balances.isEmpty() ? null : balances.getFirst();
    }

    // 팀 규칙의 본문 모양: 공통 칸 7개 + data. 이벤트 record에 없는 칸(eventType 등)도 들어 있다
    private String envelope(DomainEvent event) {
        @SuppressWarnings("unchecked")
        Map<String, Object> fields = jsonMapper.convertValue(event, Map.class);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventId", fields.get("eventId"));
        body.put("eventType", event.eventType());
        body.put("schemaVersion", event.schemaVersion());
        body.put("occurredAt", fields.get("occurredAt"));
        body.put("producer", event.producer());
        body.put("aggregateId", event.aggregateId());
        body.put("correlationId", event.correlationId());
        body.put("data", fields.get("data"));
        return jsonMapper.writeValueAsString(body);
    }

    private RecordMetadata send(String eventType, String body) throws Exception {
        ProducerRecord<String, String> record =
            new ProducerRecord<>(SETTLEMENT_TOPIC, String.valueOf(settlementId), body);
        record.headers().add("eventId", UUID.randomUUID().toString().getBytes(UTF_8));
        record.headers().add("eventType", eventType.getBytes(UTF_8));
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(Map.of(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class))) {
            return producer.send(record).get(10, TimeUnit.SECONDS);
        }
    }

    // 정산이 선언할 토픽과 같게 파티션 3개로 만든다(팀 규칙). 이미 있으면 그대로 쓴다
    private void createSettlementTopic() {
        try (Admin admin = Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG,
            kafka.getBootstrapServers()))) {
            admin.createTopics(List.of(new NewTopic(SETTLEMENT_TOPIC, 3, (short) 1))).all().get(10, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            if (!(e.getCause() instanceof TopicExistsException)) {
                throw new IllegalStateException("정산 토픽을 만들지 못했습니다", e);
            }
        } catch (Exception e) {
            throw new IllegalStateException("정산 토픽을 만들지 못했습니다", e);
        }
    }

    // 한 건씩 처리 완료를 커밋하므로, 결제 그룹의 커밋 위치가 그 메시지를 지나면 처리한 것이다
    private void awaitProcessed(RecordMetadata sent) {
        TopicPartition partition = new TopicPartition(SETTLEMENT_TOPIC, sent.partition());
        Map<String, Object> config = Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        try (Admin admin = Admin.create(config)) {
            AsyncWait.until(TIMEOUT, () -> committedOffset(admin, partition) > sent.offset());
        }
    }

    private static long committedOffset(Admin admin, TopicPartition partition) {
        try {
            var committed = admin.listConsumerGroupOffsets(GROUP).partitionsToOffsetAndMetadata()
                .get(10, TimeUnit.SECONDS).get(partition);
            return committed == null ? -1 : committed.offset();
        } catch (Exception e) {
            throw new IllegalStateException("결제 그룹의 커밋 위치를 읽지 못했습니다", e);
        }
    }

    // DLT를 처음부터 지금 끝까지 읽어 이 테스트의 키만 돌려준다
    private List<ConsumerRecord<String, String>> deadLetters() {
        TopicPartition partition = new TopicPartition(SETTLEMENT_DLT, 0);
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class))) {
            consumer.assign(List.of(partition));
            consumer.seekToBeginning(List.of(partition));
            long end = consumer.endOffsets(List.of(partition)).get(partition);
            List<ConsumerRecord<String, String>> records = new ArrayList<>();
            while (consumer.position(partition) < end) {
                consumer.poll(Duration.ofMillis(200)).forEach(records::add);
            }
            return records.stream().filter(record -> String.valueOf(settlementId).equals(record.key())).toList();
        }
    }
}
