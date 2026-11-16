package com.bbcc.kidly.boundedcontext.auth;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.auth.app.RefreshTokenService;
import com.bbcc.kidly.boundedcontext.member.MemberFixture;
import com.bbcc.kidly.boundedcontext.member.app.AdminMemberService;
import com.bbcc.kidly.boundedcontext.member.app.MemberService;
import com.bbcc.kidly.boundedcontext.member.app.dto.MemberSuspendRequest;
import com.bbcc.kidly.boundedcontext.member.domain.MemberRepository;
import com.bbcc.kidly.member.support.error.BusinessException;
import com.bbcc.kidly.shared.auth.PrincipalId;
import com.bbcc.kidly.shared.auth.PrincipalType;
import com.bbcc.kidly.shared.auth.out.MemberTokenRevoker;
import com.bbcc.kidly.shared.auth.event.AuthTokensRevokedEvent;
import com.bbcc.kidly.shared.event.DomainEvent;
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
 * 다른 테스트의 인증 이벤트도 같은 토픽에 쌓이므로 헤더 eventId로 이 테스트의 메시지를 찾는다.
 */
@KafkaIntegrationTest
@RecordApplicationEvents
class AuthKafkaPublishIntegrationTest {

    private static final String TOPIC = "kidly.auth.events";
    private static final String TOKENS_REVOKED = "auth.tokens-revoked";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    @Autowired
    private ApplicationEvents events;

    @Autowired
    private MemberTokenRevoker memberTokenRevoker;

    @Autowired
    private MemberService memberService;

    @Autowired
    private AdminMemberService adminMemberService;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

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
            ConsumerConfig.GROUP_ID_CONFIG, "auth-kafka-test-" + UUID.randomUUID(),
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
    @DisplayName("토큰을 무효화하면 커밋 뒤 auth.tokens-revoked를 주체 ID 키로 보내고 data에 주체 종류 · ID · 기준 시각을 담는다")
    void tokensRevoked() {
        var revokedAt = LocalDateTime.of(2026, 10, 12, 9, 0, 5);

        transactionTemplate.executeWithoutResult(status -> memberTokenRevoker.revoke(7L, revokedAt));

        var record = awaitRecord(firstTokensRevoked());
        assertThat(record.key()).isEqualTo("7");
        assertThat(header(record, "eventType")).isEqualTo(TOKENS_REVOKED);
        assertThat(record.headers().lastHeader("__TypeId__")).isNull();
        var body = JSON_MAPPER.readTree(record.value());
        assertThat(body.propertyNames()).containsExactly("eventId", "eventType", "schemaVersion", "occurredAt",
            "producer", "aggregateId", "correlationId", "data");
        assertThat(body.get("producer").asString()).isEqualTo("auth");
        var data = body.get("data");
        assertThat(data.get("principalType").asString()).isEqualTo("MEMBER");
        assertThat(data.get("principalId").asLong()).isEqualTo(7L);
        assertThat(data.get("revokedAt").asString()).isEqualTo("2026-10-12T09:00:05");
    }

    @Test
    @DisplayName("탈퇴하면 실제 호출자(MemberService)가 커밋한 뒤에도 auth.tokens-revoked가 회원 ID 키로 온다")
    void withdrawSendsTokensRevoked() {
        var member = memberRepository.save(MemberFixture.kakaoMember());

        memberService.withdraw(member.getId());

        assertSent(firstTokensRevoked(), member.getId());
    }

    @Test
    @DisplayName("정지하면 실제 호출자(AdminMemberService)가 커밋한 뒤에도 auth.tokens-revoked가 회원 ID 키로 온다")
    void suspendSendsTokensRevoked() {
        var member = memberRepository.save(MemberFixture.kakaoMember());

        adminMemberService.suspend(member.getId(), 999L, new MemberSuspendRequest("규칙 위반"));

        assertSent(firstTokensRevoked(), member.getId());
    }

    @Test
    @DisplayName("Refresh Token 재사용을 감지하면 401로 끝나도 무효화는 커밋되어 auth.tokens-revoked가 회원 ID 키로 온다")
    void reuseDetectionSendsTokensRevoked() {
        var memberId = memberRepository.save(MemberFixture.kakaoMember()).getId();
        var oldToken = refreshTokenService.issue(PrincipalId.member(memberId));
        refreshTokenService.reissue(PrincipalType.MEMBER, oldToken);
        rotatedSecondsAgo(memberId, 11);

        assertThatThrownBy(() -> refreshTokenService.reissue(PrincipalType.MEMBER, oldToken))
            .isInstanceOf(BusinessException.class);

        assertSent(firstTokensRevoked(), memberId);
    }

    @Test
    @DisplayName("토큰 무효화가 롤백되면 보내지 않는다")
    void tokensRevokedRolledBack() {
        var memberId = memberRepository.save(MemberFixture.kakaoMember()).getId();
        transactionTemplate.executeWithoutResult(status -> {
            memberTokenRevoker.revoke(memberId, LocalDateTime.now());
            status.setRollbackOnly();
        });
        var rolledBack = firstTokensRevoked();
        transactionTemplate.executeWithoutResult(status -> memberTokenRevoker.revoke(memberId, LocalDateTime.now()));

        // 키(회원 ID)가 같아 같은 파티션이므로, 뒤에 커밋한 무효화를 받았으면 앞의 무효화는 이미 왔어야 한다
        var committed = events.stream(AuthTokensRevokedEvent.class)
            .filter(event -> !event.eventId().equals(rolledBack.eventId()))
            .findFirst().orElseThrow();
        awaitRecord(committed);
        assertThat(findRecord(rolledBack)).isEmpty();
    }

    private AuthTokensRevokedEvent firstTokensRevoked() {
        return events.stream(AuthTokensRevokedEvent.class).findFirst().orElseThrow();
    }

    private void assertSent(DomainEvent event, Long key) {
        var record = awaitRecord(event);
        assertThat(record.key()).isEqualTo(String.valueOf(key));
        assertThat(header(record, "eventType")).isEqualTo(TOKENS_REVOKED);
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

    private void rotatedSecondsAgo(Long memberId, int seconds) {
        jdbcTemplate.update(
            "UPDATE auth.refresh_token SET rotated_at = rotated_at - make_interval(secs => ?) "
                + "WHERE principal_type = 'MEMBER' AND principal_id = ?",
            seconds, memberId);
    }

    private static String header(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), UTF_8);
    }
}
