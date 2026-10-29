package com.bbcc.kidly.boundedcontext.childprofile;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.childprofile.app.ChildProfileService;
import com.bbcc.kidly.boundedcontext.childprofile.app.dto.ChildProfileRequest;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.childprofile.domain.Gender;
import com.bbcc.kidly.shared.childprofile.domain.PreferredFit;
import com.bbcc.kidly.shared.childprofile.event.ChildChangedEvent;
import com.bbcc.kidly.shared.event.DomainEvent;
import com.bbcc.kidly.shared.member.event.MemberWithdrawnEvent;
import com.bbcc.kidly.support.AsyncWait;
import com.bbcc.kidly.support.KafkaIntegrationTest;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.json.JsonMapper;

/**
 * 자녀 프로필 이벤트가 커밋 뒤 kidly.childprofile.events에 규칙대로(키 · 헤더 · 본문) 도착하는지 본다(#548).
 * 받는 쪽(주문 · 추천)이 Spring 이벤트로 받는 것은 기존 테스트가 본다.
 * 다른 테스트의 자녀 이벤트도 같은 토픽에 쌓이므로 헤더 eventId로 이 테스트의 메시지를 찾는다.
 */
@KafkaIntegrationTest
@RecordApplicationEvents
class ChildProfileKafkaPublishIntegrationTest {

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
    private EventPublisher eventPublisher;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private KafkaContainer kafka;

    private KafkaConsumer<String, String> consumer;
    private final List<ConsumerRecord<String, String>> received = new ArrayList<>();

    @BeforeEach
    void subscribe() {
        consumer = new KafkaConsumer<>(Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
            ConsumerConfig.GROUP_ID_CONFIG, "childprofile-kafka-test-" + UUID.randomUUID(),
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
    @DisplayName("자녀를 등록하면 자녀 ID 키로 보내고 헤더 eventType · eventId와 본문 8칸을 채운다")
    void registered() {
        var childId = childProfileService.register(MEMBER_ID, REQUEST).id();

        var changed = events.stream(ChildChangedEvent.class).findFirst().orElseThrow();
        var record = awaitRecord(changed);
        assertThat(record.key()).isEqualTo(String.valueOf(childId));
        assertThat(header(record, "eventType")).isEqualTo("child.changed");
        assertThat(record.headers().lastHeader("__TypeId__")).isNull();
        assertThat(JSON_MAPPER.readTree(record.value()).propertyNames()).containsExactly("eventId", "eventType",
            "schemaVersion", "occurredAt", "producer", "aggregateId", "correlationId", "data");
    }

    @Test
    @DisplayName("자녀를 삭제하면 deleted가 true인 child.changed를 자녀 ID 키로 보낸다")
    void deleted() {
        var childId = childProfileService.register(MEMBER_ID, REQUEST).id();

        childProfileService.delete(MEMBER_ID, childId);

        var deleted = events.stream(ChildChangedEvent.class)
            .filter(event -> event.data().deleted())
            .findFirst().orElseThrow();
        assertSent(deleted, "child.changed", childId);
        assertThat(JSON_MAPPER.readTree(awaitRecord(deleted).value()).get("data").get("deleted").asBoolean())
            .isTrue();
    }

    @Test
    @DisplayName("회원이 탈퇴하면 리스너가 다시 발행한 자녀 삭제 이벤트도 보낸다")
    void deletedOnMemberWithdrawn() {
        var childId = childProfileService.register(MEMBER_ID, REQUEST).id();

        // 탈퇴 리스너는 발행한 트랜잭션이 커밋된 뒤 새 트랜잭션에서 돈다. 그 트랜잭션이 커밋돼야 자녀 이벤트가 나간다
        var withdrawn = new MemberWithdrawnEvent(UUID.randomUUID(), LocalDateTime.now(),
            new MemberWithdrawnEvent.Data(MEMBER_ID));
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(withdrawn));

        var deleted = events.stream(ChildChangedEvent.class)
            .filter(event -> event.data().deleted())
            .findFirst().orElseThrow();
        assertSent(deleted, "child.changed", childId);
    }

    @Test
    @DisplayName("롤백되면 보내지 않는다")
    void rolledBack() {
        var childId = childProfileService.register(MEMBER_ID, REQUEST).id();
        var rolledBack = new ChildChangedEvent(UUID.randomUUID(), LocalDateTime.now(), new ChildChangedEvent.Data(
            childId, MEMBER_ID, new BigDecimal("116.0"), new BigDecimal("21.0"), Gender.MALE,
            LocalDate.of(2019, 1, 15), BodyType.NORMAL, PreferredFit.LOOSE, false));
        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publish(rolledBack);
            status.setRollbackOnly();
        });
        childProfileService.delete(MEMBER_ID, childId);

        // 키(자녀 ID)가 같아 같은 파티션이므로, 뒤에 보낸 것을 받았으면 앞에서 보낸 것은 이미 왔어야 한다
        var committed = events.stream(ChildChangedEvent.class)
            .filter(event -> event.data().deleted())
            .findFirst().orElseThrow();
        awaitRecord(committed);
        assertThat(findRecord(rolledBack)).isEmpty();
    }

    private void assertSent(DomainEvent event, String eventType, Long key) {
        var record = awaitRecord(event);
        assertThat(record.key()).isEqualTo(String.valueOf(key));
        assertThat(header(record, "eventType")).isEqualTo(eventType);
    }

    // 전송은 비동기라 메시지가 올 때까지 읽는다. 받은 뒤 끝내므로 다음 테스트에 끼어들지 않는다
    private ConsumerRecord<String, String> awaitRecord(DomainEvent event) {
        AsyncWait.until(TIMEOUT, () -> {
            consumer.poll(Duration.ofMillis(200)).forEach(received::add);
            return findRecord(event).isPresent();
        });
        return findRecord(event).orElseThrow();
    }

    private Optional<ConsumerRecord<String, String>> findRecord(DomainEvent event) {
        return received.stream()
            .filter(record -> event.eventId().toString().equals(header(record, "eventId")))
            .findFirst();
    }

    private static String header(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), UTF_8);
    }
}
