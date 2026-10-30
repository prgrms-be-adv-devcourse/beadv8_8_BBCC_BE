package com.bbcc.kidly.boundedcontext.member;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.member.app.MemberService;
import com.bbcc.kidly.boundedcontext.member.app.dto.MemberUpdateRequest;
import com.bbcc.kidly.boundedcontext.member.app.outbox.MemberOutboxRelayService;
import com.bbcc.kidly.boundedcontext.member.app.support.MemberRegistrar;
import com.bbcc.kidly.boundedcontext.member.domain.MemberRegisterInfo;
import com.bbcc.kidly.boundedcontext.member.domain.MemberRepository;
import com.bbcc.kidly.boundedcontext.member.domain.Nickname;
import com.bbcc.kidly.boundedcontext.member.domain.OAuthProvider;
import com.bbcc.kidly.boundedcontext.member.domain.SocialIdentity;
import com.bbcc.kidly.boundedcontext.member.domain.outbox.MemberOutboxMessage;
import com.bbcc.kidly.boundedcontext.member.domain.outbox.MemberOutboxRepository;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.event.DomainEvent;
import com.bbcc.kidly.shared.member.event.MemberChangedEvent;
import com.bbcc.kidly.shared.member.event.MemberRegisteredEvent;
import com.bbcc.kidly.shared.member.event.MemberWithdrawnEvent;
import com.bbcc.kidly.support.AsyncWait;
import com.bbcc.kidly.support.KafkaIntegrationTest;
import java.time.Duration;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.json.JsonMapper;

/**
 * 회원 이벤트가 커밋 뒤 kidly.member.events에 규칙대로(키 · 헤더 · 본문) 도착하는지 본다(#548).
 * 받는 쪽이 받는 것은 그 도메인의 테스트가 본다.
 * 탈퇴는 아웃박스 릴레이로 보낸다(#562). 테스트 설정에서 릴레이 주기를 길게 두었으므로 릴레이는 테스트가 직접 부른다.
 * 다른 테스트의 회원 이벤트도 같은 토픽에 쌓이므로 헤더 eventId로 이 테스트의 메시지를 찾는다.
 */
@KafkaIntegrationTest
@RecordApplicationEvents
class MemberKafkaPublishIntegrationTest {

    private static final String TOPIC = "kidly.member.events";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    @Autowired
    private ApplicationEvents events;

    @Autowired
    private MemberRegistrar memberRegistrar;

    @Autowired
    private MemberService memberService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private EventPublisher eventPublisher;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private KafkaContainer kafka;

    @Autowired
    private MemberOutboxRelayService relayService;

    @Autowired
    private MemberOutboxRepository outboxRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private KafkaConsumer<String, String> consumer;
    private final List<ConsumerRecord<String, String>> received = new ArrayList<>();

    @BeforeEach
    void subscribe() {
        consumer = new KafkaConsumer<>(Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
            ConsumerConfig.GROUP_ID_CONFIG, "member-kafka-test-" + UUID.randomUUID(),
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
    @DisplayName("가입하면 회원 ID 키로 보내고 헤더 eventType · eventId와 본문 8칸을 채운다")
    void registered() {
        var member = memberRegistrar.register(new MemberRegisterInfo(
            new SocialIdentity(OAuthProvider.KAKAO, "kakao-1"), new Nickname("테스트회원"), MemberFixture.EMAIL));

        var registered = events.stream(MemberRegisteredEvent.class).findFirst().orElseThrow();
        var record = awaitRecord(registered);
        assertThat(record.key()).isEqualTo(String.valueOf(member.getId()));
        assertThat(header(record, "eventType")).isEqualTo("member.registered");
        assertThat(record.headers().lastHeader("__TypeId__")).isNull();
        assertThat(JSON_MAPPER.readTree(record.value()).propertyNames()).containsExactly("eventId", "eventType",
            "schemaVersion", "occurredAt", "producer", "aggregateId", "correlationId", "data");
    }

    @Test
    @DisplayName("닉네임을 바꾸면 member.changed를 회원 ID 키로 보낸다")
    void changed() {
        var member = memberRepository.save(MemberFixture.kakaoMember());

        memberService.update(member.getId(), new MemberUpdateRequest("새닉네임"));

        var changed = events.stream(MemberChangedEvent.class).findFirst().orElseThrow();
        assertSent(changed, "member.changed", member.getId());
    }

    @Test
    @DisplayName("탈퇴는 커밋 뒤 바로 보내지 않고, 아웃박스 릴레이가 회원 ID 키로 보내며 보낸 시각이 남는다")
    void withdrawnViaOutboxRelay() {
        var member = memberRepository.save(MemberFixture.kakaoMember());
        memberService.withdraw(member.getId());
        var withdrawn = events.stream(MemberWithdrawnEvent.class).findFirst().orElseThrow();

        // 같은 회원 키로 커밋 뒤 바로 보내는 이벤트를 하나 더 보낸다. 같은 파티션이라, 이것이 왔을 때 탈퇴가 아직 없으면
        // 탈퇴는 커밋 뒤 전송으로 나가지 않은 것이다
        var changed = new MemberChangedEvent(UUID.randomUUID(), LocalDateTime.now(),
            new MemberChangedEvent.Data(member.getId(), "확인용닉네임"));
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(changed));
        awaitRecord(changed);
        assertThat(findRecord(withdrawn)).isEmpty();

        relayService.relay();

        assertSent(withdrawn, "member.withdrawn", member.getId());
        assertThat(outboxRow(withdrawn.eventId())).containsEntry("attempt_count", 1)
            .extractingByKey("sent_at").isNotNull();
    }

    @Test
    @DisplayName("릴레이가 보내다 실패하면 그 메시지에 실패를 남기고 뒤 메시지는 보내지 않으며, 다음 실행은 실패한 메시지부터 다시 보낸다")
    void stopAtFailureAndRetryFromSameMessage() {
        // 브로커의 토픽 자동 생성이 꺼져 있어 선언하지 않은 토픽으로는 보내지 못한다
        var failingId = UUID.randomUUID();
        var afterId = UUID.randomUUID();
        outboxRepository.save(MemberOutboxMessage.create(new MemberOutboxMessage.CreateInfo(
            failingId, "member.withdrawn", "kidly.member.missing-" + failingId, "1",
            MemberOutboxMessageFixture.PAYLOAD)));
        outboxRepository.save(MemberOutboxMessage.create(MemberOutboxMessageFixture.withdrawn(afterId)));

        relayService.relay();

        assertThat(outboxRow(failingId)).containsEntry("attempt_count", 1)
            .containsEntry("sent_at", null).extractingByKey("last_error").isNotNull();
        assertThat(outboxRow(afterId)).containsEntry("attempt_count", 0).containsEntry("sent_at", null);

        relayService.relay();

        assertThat(outboxRow(failingId)).containsEntry("attempt_count", 2).containsEntry("sent_at", null);
        assertThat(outboxRow(afterId)).containsEntry("attempt_count", 0).containsEntry("sent_at", null);
    }

    @Test
    @DisplayName("롤백되면 보내지 않는다")
    void rolledBack() {
        var member = memberRepository.save(MemberFixture.kakaoMember());
        var rolledBack = new MemberChangedEvent(UUID.randomUUID(), LocalDateTime.now(),
            new MemberChangedEvent.Data(member.getId(), "롤백닉네임"));
        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publish(rolledBack);
            status.setRollbackOnly();
        });
        memberService.update(member.getId(), new MemberUpdateRequest("새닉네임"));

        // 키(회원 ID)가 같아 같은 파티션이므로, 뒤에 보낸 것을 받았으면 앞에서 보낸 것은 이미 왔어야 한다
        var committed = events.stream(MemberChangedEvent.class)
            .filter(event -> !event.eventId().equals(rolledBack.eventId()))
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

    private Map<String, Object> outboxRow(UUID eventId) {
        return jdbcTemplate.queryForMap(
            "select attempt_count, sent_at, last_error from member.member_outbox where event_id = ?", eventId);
    }

    private static String header(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), UTF_8);
    }
}
