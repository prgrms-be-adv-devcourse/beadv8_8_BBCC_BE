package com.bbcc.kidly.boundedcontext.activity.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.favorite.app.FavoriteService;
import com.bbcc.kidly.boundedcontext.favorite.app.port.in.MemberFavoritesRemover;
import com.bbcc.kidly.boundedcontext.review.app.ReviewModerationAdminService;
import com.bbcc.kidly.boundedcontext.review.app.ReviewModerationRecorder;
import com.bbcc.kidly.boundedcontext.review.app.ReviewModerationService.Candidate;
import com.bbcc.kidly.boundedcontext.review.app.ReviewService;
import com.bbcc.kidly.boundedcontext.review.app.ReviewService.ImageUrlsInput;
import com.bbcc.kidly.boundedcontext.review.app.port.ContentModerator.Content;
import com.bbcc.kidly.boundedcontext.review.app.port.ContentModerator.FailureCause;
import com.bbcc.kidly.boundedcontext.review.app.port.ContentModerator.Result;
import com.bbcc.kidly.boundedcontext.review.app.port.in.ReviewEligibilityRecorder;
import com.bbcc.kidly.boundedcontext.review.app.port.in.ReviewEligibilityRecorder.PurchaseConfirmed;
import com.bbcc.kidly.boundedcontext.review.domain.ModerationReason;
import com.bbcc.kidly.boundedcontext.support.IntegrationTest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * 이벤트 아웃박스: 리뷰 · 찜이 실제로 바뀐 트랜잭션에서만 보낼 메시지가 한 행씩 남는다(본문 = 공통 칸 7개 + data, 키 = correlationId).
 * 바뀐 것 없는 요청 · 반영하지 않은 결과 · 작성 자격 생성은 남지 않고, 원래 트랜잭션이 롤백되면 행도 없다.
 * 테스트 프로필은 릴레이를 끄므로(kidly.kafka.producer.enabled=false) 행이 그대로 남는다.
 */
@IntegrationTest
class CommunityEventOutboxIntegrationTest {

    private static final long ME = 13L;
    private static final long PRODUCT = 14L;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private ReviewEligibilityRecorder eligibilityRecorder;

    @Autowired
    private ReviewService reviewService;

    @Autowired
    private ReviewModerationRecorder moderationRecorder;

    @Autowired
    private ReviewModerationAdminService adminService;

    @Autowired
    private FavoriteService favoriteService;

    @Autowired
    private MemberFavoritesRemover memberFavoritesRemover;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private com.bbcc.kidly.boundedcontext.activity.app.port.EventOutbox outbox;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into community.replica_product (product_id, name, thumbnail_url, display_status, "
            + "source_occurred_at, created_at, updated_at) values (?, '상품', null, 'ON_SALE', now(), now(), now())",
            PRODUCT);
    }

    @Test
    @DisplayName("리뷰 작성 · 수정 · 삭제는 review.changed(키 = 리뷰 ID)로 남고, 같은 값 수정 · 작성 자격 생성은 남지 않는다")
    void reviewLifecycle() {
        eligibilityRecorder.record(confirmed(11));
        assertThat(messages()).isEmpty();
        long id = reviewService.write(ME, "닉", "11", 4, "좋아요", ReviewService.FitInput.absent(),
            ImageUrlsInput.absent()).reviewId();
        reviewService.update(ME, id, new ReviewService.ReviewChange(true, 4, false, null));
        reviewService.update(ME, id, new ReviewService.ReviewChange(true, 2, false, null));
        reviewService.delete(ME, id);

        List<Map<String, Object>> messages = messages();
        assertThat(messages).hasSize(3).allSatisfy(message -> {
            assertThat(message).containsEntry("event_type", "review.changed")
                .containsEntry("message_key", String.valueOf(id));
            Map<String, Object> body = body(message);
            assertThat(body).containsOnlyKeys("eventId", "eventType", "schemaVersion", "occurredAt", "producer",
                "aggregateId", "correlationId", "data");
            assertThat(body).containsEntry("eventId", message.get("event_id").toString())
                .containsEntry("eventType", "review.changed").containsEntry("schemaVersion", 1)
                .containsEntry("producer", "community").containsEntry("aggregateId", String.valueOf(id))
                .containsEntry("correlationId", String.valueOf(id));
            // 발생 시각은 시간대 표시 없는 ISO 문자열(한국 시간)
            assertThat((String) body.get("occurredAt")).matches("^\\d{4}-\\d{2}-\\d{2}T[^Z+]*$");
        });
        assertThat(messages.stream().map(message -> data(message).get("visible")).toList())
            .containsExactly(true, true, false);
        assertThat(data(messages.get(1))).containsEntry("rating", 2).containsEntry("orderItemId", 11)
            .containsEntry("productId", 14).containsEntry("fit", null).containsOnlyKeys("reviewId", "orderItemId",
                "productId", "rating", "fit", "visible");
    }

    @Test
    @DisplayName("검사 결과(통과 true · 숨김 false)와 관리자 조치(숨김 확정 false · 다시 공개 true)는 바뀔 때만 남는다")
    void moderationVisibility() {
        long passed = review(31);
        long flagged = review(32);
        long stale = review(33);
        long undecided = review(34);
        jdbcTemplate.update("insert into community.review_stats (product_id, review_count, rating_sum, created_at, "
            + "updated_at) values (?, 4, 16, now(), now())", PRODUCT);

        moderationRecorder.record(candidate(passed, 0), Result.passed());
        moderationRecorder.record(candidate(flagged, 0), Result.flagged(ModerationReason.HATE));
        moderationRecorder.record(candidate(stale, 5), Result.passed());
        moderationRecorder.record(candidate(undecided, 0), Result.undecided(FailureCause.TIMEOUT));
        adminService.changeVisibility(99L, flagged, "HIDDEN");
        adminService.changeVisibility(99L, flagged, "HIDDEN");
        adminService.changeVisibility(99L, flagged, "VISIBLE");

        assertThat(messages().stream().map(message -> data(message).get("reviewId") + ":"
            + data(message).get("visible")).toList())
            .containsExactly(passed + ":true", flagged + ":false", flagged + ":false", flagged + ":true");
    }

    @Test
    @DisplayName("찜 추가 · 해제(USER) · 탈퇴 삭제(MEMBER_WITHDRAWN, 행마다)는 키 = 회원 ID로 남고, 이미 찜 · 없는 찜 해제는 남지 않는다")
    void favorites() {
        favoriteService.add(ME, String.valueOf(PRODUCT));
        favoriteService.add(ME, String.valueOf(PRODUCT));
        favoriteService.remove(ME, String.valueOf(PRODUCT));
        favoriteService.remove(ME, String.valueOf(PRODUCT));
        favoriteService.add(ME, String.valueOf(PRODUCT));
        memberFavoritesRemover.removeAll(ME);

        List<Map<String, Object>> messages = messages();
        assertThat(messages.stream().map(message -> message.get("event_type") + ":" + data(message).get("cause"))
            .toList()).containsExactly("favorite.added:null", "favorite.removed:USER", "favorite.added:null",
            "favorite.removed:MEMBER_WITHDRAWN");
        assertThat(messages).allSatisfy(message -> {
            assertThat(message).containsEntry("message_key", String.valueOf(ME));
            assertThat(data(message)).containsEntry("memberId", 13).containsEntry("productId", 14)
                .containsKey("favoriteId");
            assertThat(body(message)).containsEntry("correlationId", String.valueOf(ME));
        });
    }

    @Test
    @DisplayName("원래 트랜잭션이 롤백되면(찜 수 행이 없어 해제 실패) 아웃박스 행도 없다")
    void rollbackLeavesNoMessage() {
        favoriteService.add(ME, String.valueOf(PRODUCT));
        jdbcTemplate.update("delete from community.event_outbox");
        jdbcTemplate.update("delete from community.favorite_stats");

        assertThatThrownBy(() -> favoriteService.remove(ME, String.valueOf(PRODUCT)))
            .isInstanceOf(IllegalStateException.class);

        assertThat(messages()).isEmpty();
    }

    @Test
    @DisplayName("보낸 행 지우기 · 실패 기록 · 격리는 읽은 행과 id · eventId가 모두 같을 때만 바꾼다(id를 다시 쓴 다른 행은 그대로)")
    void guardsByEventId() {
        favoriteService.add(ME, String.valueOf(PRODUCT));
        var message = outbox.findPending(10).getFirst();
        java.util.UUID other = java.util.UUID.randomUUID();

        outbox.delete(message.id(), other);
        outbox.recordFailure(message.id(), other, LocalDateTime.now().plusMinutes(1), "TimeoutException");
        outbox.quarantine(message.id(), other, LocalDateTime.now(), "TimeoutException");
        assertThat(jdbcTemplate.queryForMap("select attempts, quarantined_at from community.event_outbox"))
            .containsEntry("attempts", 0).containsEntry("quarantined_at", null);

        outbox.delete(message.id(), message.eventId());
        assertThat(messages()).isEmpty();
    }

    private long review(long orderItemId) {
        jdbcTemplate.update("insert into community.review (order_item_id, product_id, member_id, nickname, rating, "
            + "content, created_at, updated_at, moderation_status, moderation_attempts) "
            + "values (?, ?, ?, '닉', 4, '본문', now(), now(), 'PENDING', 0)", orderItemId, PRODUCT, ME);
        return jdbcTemplate.queryForObject("select id from community.review where order_item_id = ?", Long.class,
            orderItemId);
    }

    private static Candidate candidate(long reviewId, int attempts) {
        return new Candidate(reviewId, 0, attempts, new Content("본문", List.of()));
    }

    private static PurchaseConfirmed confirmed(long orderItemId) {
        return new PurchaseConfirmed(orderItemId, 12L, ME, PRODUCT, 15L, null,
            LocalDateTime.of(2026, 11, 12, 10, 0, 0, 123_456_123));
    }

    private List<Map<String, Object>> messages() {
        return jdbcTemplate.queryForList("select event_id, event_type, message_key, payload "
            + "from community.event_outbox order by id");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> body(Map<String, Object> message) {
        return JSON.readValue((String) message.get("payload"), Map.class);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> data(Map<String, Object> message) {
        return (Map<String, Object>) body(message).get("data");
    }
}
