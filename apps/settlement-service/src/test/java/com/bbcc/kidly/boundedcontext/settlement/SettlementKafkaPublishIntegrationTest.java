package com.bbcc.kidly.boundedcontext.settlement;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.settlement.app.outbox.SettlementOutboxRelayResult;
import com.bbcc.kidly.boundedcontext.settlement.app.outbox.SettlementOutboxRelayService;
import com.bbcc.kidly.boundedcontext.settlement.domain.outbox.SettlementOutboxMessage;
import com.bbcc.kidly.boundedcontext.settlement.domain.outbox.SettlementOutboxRepository;
import com.bbcc.kidly.support.KafkaIntegrationTest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.kafka.KafkaContainer;

/**
 * 정산 아웃박스 메시지가 릴레이를 거쳐 kidly.settlement.events에 규칙대로(키 · 헤더 · 본문) 도착하는지 본다.
 * 스케줄러는 테스트 설정으로 멈춰 두고 릴레이를 직접 부른다.
 * 다른 테스트의 메시지도 같은 토픽에 쌓이므로 헤더 eventId로 이 테스트의 메시지를 찾는다.
 */
@KafkaIntegrationTest
class SettlementKafkaPublishIntegrationTest {

    private static final String TOPIC = "kidly.settlement.events";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final String SETTLEMENT_ID = "31";
    private static final String SECRET_PAYLOAD = "{\"sellerId\":10,\"amount\":270000}";

    @Autowired
    private SettlementOutboxRelayService relayService;

    @Autowired
    private SettlementOutboxRepository outboxRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private KafkaContainer kafka;

    private KafkaConsumer<String, String> consumer;
    private final List<ConsumerRecord<String, String>> received = new ArrayList<>();

    @BeforeEach
    void subscribe() {
        consumer = new KafkaConsumer<>(Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
            ConsumerConfig.GROUP_ID_CONFIG, "settlement-kafka-test-" + UUID.randomUUID(),
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class));
        consumer.subscribe(List.of(TOPIC));
    }

    @AfterEach
    void close() {
        consumer.close();
    }

    @Test
    @DisplayName("릴레이는 id 순서대로 정산서 ID 키 · eventType, eventId 헤더 · 저장된 본문 그대로 보내고 보낸 시각을 기록한다")
    void relayInIdOrder() {
        List<Long> ids = List.of(saveMessage(TOPIC), saveMessage(TOPIC), saveMessage(TOPIC));

        SettlementOutboxRelayResult result = relayService.relay();

        assertThat(result).isEqualTo(
            new SettlementOutboxRelayResult(SettlementOutboxRelayResult.Outcome.COMPLETED, 3, 3));
        List<String> eventIds = ids.stream().map(id -> eventIdOf(id).toString()).toList();
        awaitEventId(eventIds.getLast());
        // 키가 같아 같은 파티션이므로 받은 순서가 보낸 순서다
        List<ConsumerRecord<String, String>> mine = received.stream()
            .filter(record -> eventIds.contains(header(record, "eventId")))
            .toList();
        assertThat(mine).extracting(record -> header(record, "eventId")).containsExactlyElementsOf(eventIds);
        assertThat(mine).allSatisfy(record -> {
            assertThat(record.key()).isEqualTo(SETTLEMENT_ID);
            assertThat(record.value()).isEqualTo(SECRET_PAYLOAD);
            assertThat(header(record, "eventType")).isEqualTo("settlement.payout-requested");
            assertThat(record.headers().lastHeader("__TypeId__")).isNull();
            assertThat(record.headers().toArray()).hasSize(2);
        });
        assertThat(jdbcTemplate.queryForList(
            "select id from settlement.settlement_outbox where sent_at is not null and attempt_count = 1",
            Long.class)).containsExactlyInAnyOrderElementsOf(ids);
    }

    @Test
    @DisplayName("보내지 못하면 실패를 기록하고 멈춘다. 뒤 메시지는 보내지 않은 채 남는다")
    void stopAtFailure() {
        // 토픽 자동 생성이 꺼져 있어 선언하지 않은 토픽은 메타데이터 대기(max.block 3초) 뒤 실패한다
        Long failed = saveMessage("undeclared-" + UUID.randomUUID());
        Long after = saveMessage(TOPIC);

        SettlementOutboxRelayResult result = relayService.relay();

        assertThat(result).isEqualTo(
            new SettlementOutboxRelayResult(SettlementOutboxRelayResult.Outcome.STOPPED, 2, 0));
        Map<String, Object> row = jdbcTemplate.queryForMap(
            "select sent_at, attempt_count, last_error from settlement.settlement_outbox where id = ?", failed);
        assertThat(row).containsEntry("sent_at", null).containsEntry("attempt_count", 1);
        assertThat((String) row.get("last_error")).contains("after 3000 ms").doesNotContain(SECRET_PAYLOAD);
        assertThat(jdbcTemplate.queryForObject(
            "select attempt_count from settlement.settlement_outbox where id = ?", Integer.class, after)).isZero();
    }

    private void awaitEventId(String eventId) {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            consumer.poll(Duration.ofMillis(200)).forEach(received::add);
            if (received.stream().anyMatch(record -> eventId.equals(header(record, "eventId")))) {
                return;
            }
        }
        throw new AssertionError(TIMEOUT.toSeconds() + "초 안에 eventId=" + eventId + " 메시지가 오지 않았습니다");
    }

    private Long saveMessage(String topic) {
        return outboxRepository.save(SettlementOutboxMessage.create(UUID.randomUUID(), "settlement.payout-requested",
            topic, SETTLEMENT_ID, SECRET_PAYLOAD)).getId();
    }

    private UUID eventIdOf(Long id) {
        return jdbcTemplate.queryForObject("select event_id from settlement.settlement_outbox where id = ?",
            UUID.class, id);
    }

    private static String header(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), UTF_8);
    }
}
