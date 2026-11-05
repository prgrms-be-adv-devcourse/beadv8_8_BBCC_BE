package com.bbcc.kidly.boundedcontext.review.in.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.support.AsyncWait;
import com.bbcc.kidly.boundedcontext.support.KafkaIntegrationTest;
import com.bbcc.kidly.shared.member.event.MemberWithdrawnEvent;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.json.JsonMapper;

/**
 * 커뮤니티가 kidly.member.events의 member.withdrawn을 그룹 kidly.community로 받는다(키 = 회원 ID).
 * 처리 실패는 그 회원 리뷰 행만 막는 CHECK 제약(NOT VALID)으로 만든다.
 */
@KafkaIntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class CommunityMemberEventKafkaIntegrationTest {

    private static final String TOPIC = "kidly.member.events";
    private static final String BREAK_REVIEW = "test_break_community_review_withdrawn";
    private static final long RETRY_BACKOFF_TOTAL_MS = 3_000;
    private static final LocalDateTime WITHDRAWN_AT = LocalDateTime.of(2026, 11, 2, 10, 0, 0, 123_456_123);

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
    void startListener() {
        kafkaClient = new CommunityKafkaTestClient(kafka, jsonMapper);
        listener = kafkaClient.start(guard, CommunityMemberEventKafkaListener.ID, TOPIC);
        deadLetterListener = kafkaClient.start(guard, CommunityDeadLetterKafkaListener.ID, TOPIC + ".community.dlt");
        kafkaClient.awaitCaughtUp("kidly.order.events.community.dlt");
        kafkaClient.awaitCaughtUp("kidly.product.events.community.dlt");
    }

    @AfterEach
    void stopListener() {
        listener.stop();
        deadLetterListener.stop();
        jdbcTemplate.execute("alter table community.review drop constraint if exists " + BREAK_REVIEW);
    }

    @Test
    @DisplayName("member.withdrawn을 받아 찜을 지우고 리뷰 작성자를 탈퇴로 표시하며, 두 번 받아도 1회분이다")
    void withdrawnAppliedOnce() {
        long member = newId();
        favorite(member, 14);
        review(member, 201);
        var event = withdrawn(member);

        kafkaClient.awaitProcessed(kafkaClient.send(TOPIC, event));
        Map<String, Object> first = review(201L);
        kafkaClient.awaitProcessed(kafkaClient.send(TOPIC, event));

        assertThat(jdbcTemplate.queryForObject("select count(*) from community.favorite where member_id = ?",
            Integer.class, member)).isZero();
        assertThat(first.get("nickname")).isNull();
        assertThat(first.get("author_withdrawn_at")).isNotNull();
        assertThat(review(201L)).isEqualTo(first);
        assertThat(jdbcTemplate.queryForObject("select favorite_count from community.favorite_stats "
            + "where product_id = 14", Long.class)).isZero();
        assertThat(kafkaClient.deadLetters(TOPIC, event.eventId().toString())).isEmpty();
    }

    @Test
    @DisplayName("커뮤니티가 받지 않는 회원 이벤트는 건너뛰고, 필수값(회원 ID)이 없으면 WARN 한 줄 후 무시한다(DLT 없음)")
    void skipsOtherAndMissing(CapturedOutput output) {
        String otherId = CommunityKafkaTestClient.newEventId();
        kafkaClient.awaitProcessed(kafkaClient.send(TOPIC, "1", "member.changed", otherId, "커뮤니티가 읽지 않는 본문"));
        var missing = new MemberWithdrawnEvent(UUID.randomUUID(), WITHDRAWN_AT, new MemberWithdrawnEvent.Data(null));

        kafkaClient.awaitProcessed(kafkaClient.send(TOPIC, "1", missing.eventType(), missing.eventId().toString(),
            kafkaClient.envelope(missing)));

        assertThat(kafkaClient.deadLetters(TOPIC, otherId)).isEmpty();
        assertThat(kafkaClient.deadLetters(TOPIC, missing.eventId().toString())).isEmpty();
        assertThat(output.getOut()).contains("회원 이벤트 무시", "event=member.withdrawn", "reason=missing-member-id");
    }

    @Test
    @DisplayName("처리가 실패하면 3번 재시도한 뒤 kidly.member.events.community.dlt(파티션 0)에 1건 보내고, 찜은 그대로이며 DLT는 DB에 보관된다")
    void processingFailureToDeadLetter() {
        long member = newId();
        favorite(member, 15);
        review(member, 301);
        jdbcTemplate.execute("alter table community.review add constraint " + BREAK_REVIEW
            + " check (member_id <> " + member + " or author_withdrawn_at is null) not valid");
        var event = withdrawn(member);

        RecordMetadata sent = kafkaClient.send(TOPIC, event);

        String eventId = event.eventId().toString();
        AsyncWait.until(CommunityKafkaTestClient.TIMEOUT, () -> !kafkaClient.deadLetters(TOPIC, eventId).isEmpty());
        assertThat(kafkaClient.deadLetters(TOPIC, eventId)).singleElement().satisfies(deadLetter -> {
            assertThat(deadLetter.partition()).isZero();
            assertThat(deadLetter.key()).isEqualTo(String.valueOf(member));
            assertThat(deadLetter.timestamp() - sent.timestamp()).isGreaterThanOrEqualTo(RETRY_BACKOFF_TOTAL_MS);
        });
        assertThat(jdbcTemplate.queryForObject("select count(*) from community.favorite where member_id = ?",
            Integer.class, member)).isEqualTo(1);
        // 회원 DLT도 다른 커뮤니티 DLT와 같은 리스너가 DB에 보관한다(관리자 재처리 대상)
        AsyncWait.until(CommunityKafkaTestClient.TIMEOUT, () -> jdbcTemplate.queryForObject(
            "select count(*) from community.dead_letter where event_id = ?", Integer.class, eventId) == 1);
        assertThat(jdbcTemplate.queryForObject("select source_topic from community.dead_letter where event_id = ?",
            String.class, eventId)).isEqualTo(TOPIC);
    }

    private void favorite(long memberId, long productId) {
        jdbcTemplate.update("insert into community.favorite (member_id, product_id, created_at, updated_at) "
            + "values (?, ?, now(), now())", memberId, productId);
        jdbcTemplate.update("insert into community.favorite_stats (product_id, favorite_count, created_at, updated_at) "
            + "values (?, 1, now(), now()) on conflict (product_id) do update "
            + "set favorite_count = community.favorite_stats.favorite_count + 1", productId);
    }

    private void review(long memberId, long orderItemId) {
        jdbcTemplate.update("insert into community.review (order_item_id, product_id, member_id, nickname, rating, "
            + "content, created_at, updated_at) values (?, 14, ?, '닉', 4, '본문', now(), now())",
            orderItemId, memberId);
    }

    private Map<String, Object> review(Long orderItemId) {
        return jdbcTemplate.queryForMap("select nickname, author_withdrawn_at, updated_at from community.review "
            + "where order_item_id = ?", orderItemId);
    }

    private static long newId() {
        return ThreadLocalRandom.current().nextLong(1_000_000, Long.MAX_VALUE);
    }

    private static MemberWithdrawnEvent withdrawn(long memberId) {
        return new MemberWithdrawnEvent(UUID.randomUUID(), WITHDRAWN_AT, new MemberWithdrawnEvent.Data(memberId));
    }
}
