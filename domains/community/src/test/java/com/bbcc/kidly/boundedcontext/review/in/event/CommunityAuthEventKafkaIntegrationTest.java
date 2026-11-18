package com.bbcc.kidly.boundedcontext.review.in.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.favorite.fixture.FavoriteRows;
import com.bbcc.kidly.boundedcontext.support.AsyncWait;
import com.bbcc.kidly.boundedcontext.support.KafkaIntegrationTest;
import com.bbcc.kidly.shared.auth.event.ChildAccountChangedEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.errors.TopicExistsException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.json.JsonMapper;

/**
 * 커뮤니티가 kidly.auth.events의 child-account.changed(deleted = true)를 그룹 kidly.community로 받아 그 자녀 계정의 찜을 지운다
 * (키 = 계정 ID). 처리 실패는 그 계정의 삭제 기록 INSERT만 막는 트리거로 만든다.
 */
@KafkaIntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class CommunityAuthEventKafkaIntegrationTest {

    private static final String TOPIC = "kidly.auth.events";
    private static final String BREAK_DELETION = "test_break_community_child_deletion";
    private static final LocalDateTime DELETED_AT = LocalDateTime.of(2026, 11, 16, 10, 0, 0, 123_456_123);

    @Autowired
    private KafkaContainer kafka;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private CommunityKafkaListenerTestGuard guard;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private CommunityKafkaTestClient kafkaClient;
    private MessageListenerContainer listener;
    private MessageListenerContainer deadLetterListener;

    @BeforeEach
    void startListener() throws Exception {
        createAuthTopic();
        kafkaClient = new CommunityKafkaTestClient(kafka, jsonMapper);
        listener = kafkaClient.start(guard, CommunityAuthEventKafkaListener.ID, TOPIC);
        deadLetterListener = kafkaClient.start(guard, CommunityDeadLetterKafkaListener.ID, TOPIC + ".community.dlt");
        kafkaClient.awaitCaughtUp("kidly.order.events.community.dlt");
        kafkaClient.awaitCaughtUp("kidly.product.events.community.dlt");
        kafkaClient.awaitCaughtUp("kidly.member.events.community.dlt");
    }

    @AfterEach
    void stopListener() {
        listener.stop();
        deadLetterListener.stop();
        jdbcTemplate.execute("drop trigger if exists " + BREAK_DELETION + " on community.deleted_child_account");
        jdbcTemplate.execute("drop function if exists community." + BREAK_DELETION + "()");
    }

    @Test
    @DisplayName("deleted = true를 받으면 그 자녀 계정의 찜만 지우고(같은 ID 회원 · 보호자 찜은 그대로) 계정을 남기며, 두 번 받아도 1회분이다")
    void deletionAppliedOnce() {
        long account = newId();
        long guardian = newId();
        childFavorite(account, guardian, 14);
        childFavorite(account, guardian, 15);
        memberFavorite(account, 14);
        memberFavorite(guardian, 15);
        var event = changed(account, guardian, true);

        kafkaClient.awaitProcessed(kafkaClient.send(TOPIC, event));
        kafkaClient.awaitProcessed(kafkaClient.send(TOPIC, event));

        assertThat(jdbcTemplate.queryForList("select owner_type || ':' || product_id from community.favorite "
            + "where owner_id in (?, ?) order by owner_id = ? desc, product_id", String.class, account, guardian,
            account)).containsExactly("MEMBER:14", "MEMBER:15");
        assertThat(jdbcTemplate.queryForObject("select guardian_id from community.deleted_child_account "
            + "where account_id = ?", Long.class, account)).isEqualTo(guardian);
        assertThat(count(14)).isEqualTo(1);
        assertThat(count(15)).isEqualTo(1);
        assertThat(kafkaClient.deadLetters(TOPIC, event.eventId().toString())).isEmpty();
    }

    @Test
    @DisplayName("만들어짐(deleted = false) · 다른 인증 이벤트는 건너뛰고, 필수값(보호자 ID)이 없으면 WARN 한 줄 후 무시한다(DLT 없음)")
    void skipsCreatedOtherAndMissing(CapturedOutput output) {
        long account = newId();
        childFavorite(account, 70, 14);
        var created = changed(account, 70L, false);
        String otherId = CommunityKafkaTestClient.newEventId();
        var missing = new ChildAccountChangedEvent(UUID.randomUUID(), DELETED_AT,
            new ChildAccountChangedEvent.Data(account, 3L, null, true));

        kafkaClient.awaitProcessed(kafkaClient.send(TOPIC, created));
        kafkaClient.awaitProcessed(kafkaClient.send(TOPIC, "1", "auth.token-revoked", otherId, "커뮤니티가 읽지 않는 본문"));
        kafkaClient.awaitProcessed(kafkaClient.send(TOPIC, missing));

        assertThat(jdbcTemplate.queryForObject("select count(*) from community.favorite where owner_type = 'CHILD' "
            + "and owner_id = ?", Integer.class, account)).isOne();
        assertThat(jdbcTemplate.queryForObject("select count(*) from community.deleted_child_account "
            + "where account_id = ?", Integer.class, account)).isZero();
        assertThat(kafkaClient.deadLetters(TOPIC, otherId)).isEmpty();
        assertThat(kafkaClient.deadLetters(TOPIC, missing.eventId().toString())).isEmpty();
        assertThat(output.getOut()).contains("자녀 계정 이벤트 무시", "event=child-account.changed",
            "reason=missing-guardian-id");
    }

    @Test
    @DisplayName("처리가 실패하면 재시도 뒤 kidly.auth.events.community.dlt(파티션 0)에 보내고, 찜은 그대로이며 DLT는 DB에 보관된다")
    void processingFailureToDeadLetter() {
        long account = newId();
        childFavorite(account, 70, 16);
        breakDeletionOf(account);
        var event = changed(account, 70L, true);

        kafkaClient.send(TOPIC, event);

        String eventId = event.eventId().toString();
        AsyncWait.until(CommunityKafkaTestClient.TIMEOUT, () -> !kafkaClient.deadLetters(TOPIC, eventId).isEmpty());
        assertThat(kafkaClient.deadLetters(TOPIC, eventId)).singleElement().satisfies(deadLetter -> {
            assertThat(deadLetter.partition()).isZero();
            assertThat(deadLetter.key()).isEqualTo(String.valueOf(account));
            assertThat(CommunityKafkaTestClient.header(deadLetter, "eventType")).isEqualTo("child-account.changed");
            assertThat(CommunityKafkaTestClient.header(deadLetter, KafkaHeaders.DLT_ORIGINAL_TOPIC)).isEqualTo(TOPIC);
        });
        assertThat(jdbcTemplate.queryForObject("select count(*) from community.favorite where owner_type = 'CHILD' "
            + "and owner_id = ?", Integer.class, account)).isOne();
        AsyncWait.until(CommunityKafkaTestClient.TIMEOUT, () -> jdbcTemplate.queryForObject(
            "select count(*) from community.dead_letter where event_id = ?", Integer.class, eventId) == 1);
        assertThat(jdbcTemplate.queryForObject("select source_topic from community.dead_letter where event_id = ?",
            String.class, eventId)).isEqualTo(TOPIC);
    }

    // 보내는 쪽(회원 서비스)이 만드는 토픽이라 테스트 브로커(자동 생성 꺼짐)에 직접 만든다. 이미 있으면 그대로 둔다
    private void createAuthTopic() throws Exception {
        try (var admin = AdminClient.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG,
            kafka.getBootstrapServers()))) {
            if (!admin.listTopics().names().get(10, TimeUnit.SECONDS).contains(TOPIC)) {
                try {
                    admin.createTopics(List.of(new NewTopic(TOPIC, 3, (short) 1))).all().get(10, TimeUnit.SECONDS);
                } catch (ExecutionException e) {
                    if (!(e.getCause() instanceof TopicExistsException)) {
                        throw e;
                    }
                }
            }
        }
    }

    // 그 계정의 삭제 기록 INSERT가 늘 CHECK 위반(23514)으로 실패하게 한다. 테스트가 끝나면 지운다
    private void breakDeletionOf(long accountId) {
        jdbcTemplate.execute("create function community." + BREAK_DELETION + "() returns trigger language plpgsql "
            + "as $$ begin raise exception 'test child deletion failure' using errcode = 'check_violation'; end $$");
        jdbcTemplate.execute("create trigger " + BREAK_DELETION + " before insert on community.deleted_child_account "
            + "for each row when (new.account_id = " + accountId + ") execute function community." + BREAK_DELETION
            + "()");
    }

    private void childFavorite(long accountId, long guardianId, long productId) {
        FavoriteRows rows = new FavoriteRows(jdbcTemplate);
        rows.childFavorite(accountId, guardianId, productId).insert();
        rows.increaseFavoriteCount(productId);
    }

    private void memberFavorite(long memberId, long productId) {
        FavoriteRows rows = new FavoriteRows(jdbcTemplate);
        rows.memberFavorite(memberId, productId).insert();
        rows.increaseFavoriteCount(productId);
    }

    private long count(long productId) {
        return jdbcTemplate.queryForObject("select favorite_count from community.favorite_stats where product_id = ?",
            Long.class, productId);
    }

    private static long newId() {
        return ThreadLocalRandom.current().nextLong(1_000_000, Long.MAX_VALUE);
    }

    private static ChildAccountChangedEvent changed(long accountId, Long guardianId, boolean deleted) {
        return new ChildAccountChangedEvent(UUID.randomUUID(), DELETED_AT,
            new ChildAccountChangedEvent.Data(accountId, 3L, guardianId, deleted));
    }
}
