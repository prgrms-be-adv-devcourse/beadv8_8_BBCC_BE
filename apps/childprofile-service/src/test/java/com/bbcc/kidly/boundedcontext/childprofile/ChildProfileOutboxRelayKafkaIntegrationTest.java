package com.bbcc.kidly.boundedcontext.childprofile;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.childprofile.app.ChildProfileService;
import com.bbcc.kidly.boundedcontext.childprofile.app.dto.ChildProfileRequest;
import com.bbcc.kidly.boundedcontext.childprofile.app.outbox.ChildProfileOutboxRelayService;
import com.bbcc.kidly.boundedcontext.childprofile.domain.outbox.ChildProfileOutboxMessage;
import com.bbcc.kidly.boundedcontext.childprofile.domain.outbox.ChildProfileOutboxRepository;
import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.childprofile.domain.Gender;
import com.bbcc.kidly.shared.childprofile.domain.PreferredFit;
import com.bbcc.kidly.shared.childprofile.event.ChildChangedEvent;
import com.bbcc.kidly.support.AsyncWait;
import com.bbcc.kidly.support.KafkaIntegrationTest;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.json.JsonMapper;

/**
 * 자녀 프로필 이벤트가 커밋 직전 아웃박스에 저장된 뒤 릴레이를 거쳐 kidly.childprofile.events에 규칙대로 도착하는지
 * 실제 DB와 브로커로 본다. 테스트 설정에서 릴레이 주기를 길게 두었으므로 릴레이는 테스트가 직접 부른다.
 * 다른 테스트의 메시지도 같은 토픽에 쌓이므로 헤더 eventId로 이 테스트의 메시지를 찾는다.
 */
@KafkaIntegrationTest
@RecordApplicationEvents
class ChildProfileOutboxRelayKafkaIntegrationTest {

    private static final String TOPIC = "kidly.childprofile.events";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
    private static final Long MEMBER_ID = 1L;
    private static final ChildProfileRequest REQUEST = new ChildProfileRequest(
        "첫째", LocalDate.of(2019, 1, 15), Gender.MALE, new BigDecimal("115.0"), new BigDecimal("20.0"),
        BodyType.NORMAL, PreferredFit.LOOSE);

    @Autowired
    private ApplicationEvents events;

    @Autowired
    private ChildProfileService childProfileService;

    @Autowired
    private ChildProfileOutboxRelayService relayService;

    @Autowired
    private ChildProfileOutboxRepository outboxRepository;

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
            ConsumerConfig.GROUP_ID_CONFIG, "childprofile-outbox-relay-test-" + UUID.randomUUID(),
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
    @DisplayName("자녀를 등록하고 릴레이를 부르면 자녀 ID 키 · 헤더 2개 · 본문(공통 칸 7개 + data)으로 도착하고 보낸 시각이 남는다")
    void relayRegistered() {
        var childId = childProfileService.register(MEMBER_ID, REQUEST).id();

        relayService.relay();

        var changed = events.stream(ChildChangedEvent.class).findFirst().orElseThrow();
        var record = awaitRecord(changed.eventId());
        assertThat(record.key()).isEqualTo(String.valueOf(childId));
        assertThat(record.headers().toArray()).extracting(Header::key).containsExactly("eventType", "eventId");
        assertThat(header(record, "eventType")).isEqualTo("child.changed");
        assertThat(JSON_MAPPER.readTree(record.value()).propertyNames()).containsExactly("eventId", "eventType",
            "schemaVersion", "occurredAt", "producer", "aggregateId", "correlationId", "data");
        assertThat(outboxRow(changed.eventId())).containsEntry("attempt_count", 1)
            .extractingByKey("sent_at").isNotNull();
    }

    @Test
    @DisplayName("자녀를 삭제하고 릴레이를 부르면 deleted가 true인 child.changed가 도착한다")
    void relayDeleted() {
        var childId = childProfileService.register(MEMBER_ID, REQUEST).id();
        childProfileService.delete(MEMBER_ID, childId);

        relayService.relay();

        var deleted = deletedEvent();
        var record = awaitRecord(deleted.eventId());
        assertThat(record.key()).isEqualTo(String.valueOf(childId));
        assertThat(JSON_MAPPER.readTree(record.value()).get("data").get("deleted").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("회원 탈퇴로 자녀를 일괄 삭제하면(Kafka 탈퇴 리스너가 부르는 처리) 삭제 이벤트도 릴레이로 도착한다")
    void relayDeletedOnMemberWithdrawn() {
        var childId = childProfileService.register(MEMBER_ID, REQUEST).id();
        childProfileService.deleteAll(MEMBER_ID);

        relayService.relay();

        var record = awaitRecord(deletedEvent().eventId());
        assertThat(record.key()).isEqualTo(String.valueOf(childId));
    }

    @Test
    @DisplayName("보내다 실패하면 그 메시지에 실패를 남기고 뒤 메시지는 보내지 않으며, 다음 실행은 실패한 메시지부터 다시 보낸다")
    void stopAtFailureAndRetryFromSameMessage() {
        // 브로커의 토픽 자동 생성이 꺼져 있어 선언하지 않은 토픽으로는 보내지 못한다
        var failingId = UUID.randomUUID();
        var afterId = UUID.randomUUID();
        outboxRepository.save(ChildProfileOutboxMessage.create(new ChildProfileOutboxMessage.CreateInfo(
            failingId, "child.changed", "kidly.childprofile.missing-" + failingId, "3",
            ChildProfileOutboxMessageFixture.PAYLOAD)));
        outboxRepository.save(ChildProfileOutboxMessage.create(
            ChildProfileOutboxMessageFixture.childChanged(afterId)));

        relayService.relay();

        assertThat(outboxRow(failingId)).containsEntry("attempt_count", 1)
            .containsEntry("sent_at", null).extractingByKey("last_error").isNotNull();
        assertThat(outboxRow(afterId)).containsEntry("attempt_count", 0).containsEntry("sent_at", null);

        relayService.relay();

        assertThat(outboxRow(failingId)).containsEntry("attempt_count", 2).containsEntry("sent_at", null);
        assertThat(outboxRow(afterId)).containsEntry("attempt_count", 0).containsEntry("sent_at", null);
    }

    private ChildChangedEvent deletedEvent() {
        return events.stream(ChildChangedEvent.class)
            .filter(event -> event.data().deleted())
            .findFirst().orElseThrow();
    }

    private Map<String, Object> outboxRow(UUID eventId) {
        return jdbcTemplate.queryForMap(
            "select attempt_count, sent_at, last_error from childprofile.child_profile_outbox where event_id = ?",
            eventId);
    }

    private ConsumerRecord<String, String> awaitRecord(UUID eventId) {
        AsyncWait.until(TIMEOUT, () -> {
            consumer.poll(Duration.ofMillis(200)).forEach(received::add);
            return findRecord(eventId).isPresent();
        });
        return findRecord(eventId).orElseThrow();
    }

    private Optional<ConsumerRecord<String, String>> findRecord(UUID eventId) {
        return received.stream()
            .filter(record -> eventId.toString().equals(header(record, "eventId")))
            .findFirst();
    }

    private static String header(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), UTF_8);
    }
}
