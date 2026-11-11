package com.bbcc.kidly.boundedcontext.activity.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.activity.app.port.in.ActivityRecorder;
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
import com.bbcc.kidly.boundedcontext.review.app.port.ReviewImageStorage;
import com.bbcc.kidly.boundedcontext.review.app.port.in.ReviewEligibilityRecorder;
import com.bbcc.kidly.boundedcontext.review.app.port.in.ReviewEligibilityRecorder.PurchaseConfirmed;
import com.bbcc.kidly.boundedcontext.review.domain.ModerationReason;
import com.bbcc.kidly.boundedcontext.review.out.storage.FakeReviewImageUploadChecker;
import com.bbcc.kidly.boundedcontext.support.IntegrationTest;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * 활동 기록을 실제 DB로 확인한다: 사실이 생긴 지점(같은 트랜잭션)에서만 한 행씩 남고, 멱등 요청 · 반영하지 않은 결과 · 같은 값 관리자 요청은
 * 남지 않는다. 원래 트랜잭션이 롤백되면 행도 카운터도 없다. 카운터는 커밋 뒤에만 오른다.
 */
@IntegrationTest
class ActivityRecordingIntegrationTest {

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
    private ActivityRecorder activityRecorder;

    @Autowired
    private ReviewImageStorage imageStorage;

    @Autowired
    private FakeReviewImageUploadChecker uploadChecker;

    @Autowired
    private MeterRegistry registry;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void seed() {
        uploadChecker.reset();
        jdbcTemplate.update("insert into community.replica_product (product_id, name, thumbnail_url, display_status, "
            + "source_occurred_at, created_at, updated_at) values (?, '상품', null, 'ON_SALE', now(), now(), now())",
            PRODUCT);
    }

    @Test
    @DisplayName("구매확정으로 작성 자격이 새로 생기면 ELIGIBILITY_CREATED 한 행, 같은 구매확정을 다시 받으면 그대로")
    void eligibilityCreated() {
        eligibilityRecorder.record(confirmed(11));
        eligibilityRecorder.record(confirmed(11));

        List<Map<String, Object>> rows = rows("ELIGIBILITY_CREATED");
        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row).containsEntry("product_id", PRODUCT);
            assertThat(attributes(row)).containsExactlyInAnyOrderEntriesOf(Map.of("order_item_id", 11));
        });
        assertThat(rows.getFirst().get("aggregate_id")).isEqualTo(jdbcTemplate.queryForObject(
            "select id from community.review_eligibility where order_item_id = 11", Long.class));
    }

    @Test
    @DisplayName("리뷰 작성 · 수정(바뀜) · 삭제가 한 행씩 남고, 같은 값 수정은 남지 않는다. 카운터는 커밋마다 하나씩")
    void reviewLifecycle() {
        eligibilityRecorder.record(confirmed(11));
        double created = counter("kidly.community.reviews", "action", "created");
        double updated = counter("kidly.community.reviews", "action", "updated");
        double deleted = counter("kidly.community.reviews", "action", "deleted");
        long id = reviewService.write(ME, "닉", "11", 4, "좋아요", ReviewService.FitInput.absent(),
            ImageUrlsInput.of(List.of(own(0)))).reviewId();

        reviewService.update(ME, id, new ReviewService.ReviewChange(true, 4, false, null));
        reviewService.update(ME, id, new ReviewService.ReviewChange(true, 2, true, "고친 본문"));
        reviewService.delete(ME, id);

        assertThat(rows("REVIEW_CREATED")).singleElement().satisfies(row -> {
            assertThat(row).containsEntry("aggregate_id", id).containsEntry("product_id", PRODUCT);
            assertThat(attributes(row)).containsEntry("order_item_id", 11).containsEntry("rating", 4)
                .containsEntry("fit", null).containsEntry("image_count", 1).hasSize(4);
        });
        assertThat(rows("REVIEW_UPDATED")).singleElement().satisfies(row -> assertThat(attributes(row))
            .containsEntry("rating", 2).containsEntry("fit", null).containsEntry("image_count", 1)
            .containsEntry("content_changed", true).containsEntry("images_changed", false).hasSize(5));
        assertThat(rows("REVIEW_DELETED")).singleElement().satisfies(row -> assertThat(attributes(row))
            .containsEntry("rating", 2).containsEntry("was_public", true).hasSize(2));
        assertThat(counter("kidly.community.reviews", "action", "created")).isEqualTo(created + 1);
        assertThat(counter("kidly.community.reviews", "action", "updated")).isEqualTo(updated + 1);
        assertThat(counter("kidly.community.reviews", "action", "deleted")).isEqualTo(deleted + 1);
    }

    @Test
    @DisplayName("찜은 새로 생길 때 · 실제로 지울 때만 남는다(이미 찜 · 없는 찜 취소는 없음). 취소의 까닭은 user")
    void favoriteAddAndRemove() {
        double added = counter("kidly.community.favorites", "action", "added", "cause", "none");
        double removed = counter("kidly.community.favorites", "action", "removed", "cause", "user");

        favoriteService.add(ME, String.valueOf(PRODUCT));
        favoriteService.add(ME, String.valueOf(PRODUCT));
        Long favoriteId = jdbcTemplate.queryForObject("select id from community.favorite", Long.class);
        favoriteService.remove(ME, String.valueOf(PRODUCT));
        favoriteService.remove(ME, String.valueOf(PRODUCT));

        assertThat(rows("FAVORITE_ADDED")).singleElement().satisfies(row -> {
            assertThat(row).containsEntry("aggregate_id", favoriteId).containsEntry("product_id", PRODUCT);
            assertThat(attributes(row)).isEmpty();
        });
        assertThat(rows("FAVORITE_REMOVED")).singleElement().satisfies(row -> {
            assertThat(row).containsEntry("aggregate_id", favoriteId);
            assertThat(attributes(row)).containsExactlyInAnyOrderEntriesOf(Map.of("cause", "user"));
        });
        assertThat(counter("kidly.community.favorites", "action", "added", "cause", "none")).isEqualTo(added + 1);
        assertThat(counter("kidly.community.favorites", "action", "removed", "cause", "user")).isEqualTo(removed + 1);
    }

    @Test
    @DisplayName("회원 탈퇴로 지운 찜은 지운 행마다 까닭 withdrawn으로 남고, 카운터는 그 수만큼 오른다")
    void withdrawnFavorites() {
        for (long product = 21; product <= 23; product++) {
            jdbcTemplate.update("insert into community.replica_product (product_id, name, thumbnail_url, "
                + "display_status, source_occurred_at, created_at, updated_at) values (?, '상품', null, 'ON_SALE', "
                + "now(), now(), now())", product);
            favoriteService.add(ME, String.valueOf(product));
        }
        favoriteService.add(ME + 1, "21");
        List<Long> mine = jdbcTemplate.queryForList("select id from community.favorite where member_id = ?",
            Long.class, ME);
        Long others = jdbcTemplate.queryForObject("select id from community.favorite where member_id = ?",
            Long.class, ME + 1);
        double withdrawn = counter("kidly.community.favorites", "action", "removed", "cause", "withdrawn");

        memberFavoritesRemover.removeAll(ME);

        assertThat(rows("FAVORITE_REMOVED")).hasSize(3).allSatisfy(row ->
            assertThat(attributes(row)).containsExactlyInAnyOrderEntriesOf(Map.of("cause", "withdrawn")));
        assertThat(rows("FAVORITE_REMOVED").stream().map(row -> row.get("aggregate_id")).toList())
            .containsExactlyInAnyOrderElementsOf(mine);
        assertThat(rows("FAVORITE_REMOVED").stream().map(row -> row.get("product_id")).toList())
            .containsExactlyInAnyOrder(21L, 22L, 23L);
        // 다른 회원의 찜은 그대로 남고 기록도 없다
        assertThat(jdbcTemplate.queryForList("select id from community.favorite", Long.class))
            .containsExactly(others);
        assertThat(rows("FAVORITE_REMOVED")).noneSatisfy(row -> assertThat(row.get("aggregate_id"))
            .isEqualTo(others));
        assertThat(counter("kidly.community.favorites", "action", "removed", "cause", "withdrawn"))
            .isEqualTo(withdrawn + 3);
    }

    @Test
    @DisplayName("찜이 없는 회원이 탈퇴하면 활동 행도 카운터도 없다")
    void withdrawnWithoutFavorites() {
        double withdrawn = counter("kidly.community.favorites", "action", "removed", "cause", "withdrawn");

        assertThat(memberFavoritesRemover.removeAll(ME)).isZero();

        assertThat(jdbcTemplate.queryForObject("select count(*) from community.activity", Long.class)).isZero();
        assertThat(counter("kidly.community.favorites", "action", "removed", "cause", "withdrawn"))
            .isEqualTo(withdrawn);
    }

    @Test
    @DisplayName("검사 결과는 실제로 반영될 때만 by=moderator로 남는다(통과 · 걸림). 버전이 바뀌어 버린 결과 · 판단 못 함은 없음")
    void moderationDecisions() {
        long passed = review(31, 0, 0);
        long flagged = review(32, 0, 0);
        long stale = review(33, 0, 0);
        long undecided = review(34, 0, 0);
        jdbcTemplate.update("insert into community.review_stats (product_id, review_count, rating_sum, created_at, "
            + "updated_at) values (?, 4, 16, now(), now())", PRODUCT);
        double passedCount = counter("kidly.community.moderation.decisions", "by", "moderator", "status", "passed");
        double hiddenCount = counter("kidly.community.moderation.decisions", "by", "moderator", "status", "hidden");

        moderationRecorder.record(candidate(passed, 0), Result.passed());
        moderationRecorder.record(candidate(flagged, 0), Result.flagged(ModerationReason.HATE));
        moderationRecorder.record(candidate(stale, 5), Result.passed());
        moderationRecorder.record(candidate(undecided, 0), Result.undecided(FailureCause.TIMEOUT));

        List<Map<String, Object>> rows = rows("MODERATION_DECIDED");
        assertThat(rows).hasSize(2);
        assertThat(rows).anySatisfy(row -> {
            assertThat(row).containsEntry("aggregate_id", passed);
            assertThat(attributes(row)).containsEntry("by", "moderator").containsEntry("status", "PASSED")
                .containsEntry("reason", null);
        });
        assertThat(rows).anySatisfy(row -> {
            assertThat(row).containsEntry("aggregate_id", flagged);
            assertThat(attributes(row)).containsEntry("by", "moderator").containsEntry("status", "HIDDEN")
                .containsEntry("reason", "HATE");
        });
        assertThat(counter("kidly.community.moderation.decisions", "by", "moderator", "status", "passed"))
            .isEqualTo(passedCount + 1);
        assertThat(counter("kidly.community.moderation.decisions", "by", "moderator", "status", "hidden"))
            .isEqualTo(hiddenCount + 1);
    }

    @Test
    @DisplayName("관리자 숨김 확정 · 다시 공개는 상태가 바뀔 때만 by=admin으로 남는다(같은 값 다시 보내기는 없음)")
    void adminDecisions() {
        long id = review(41, 0, 0);
        jdbcTemplate.update("insert into community.review_stats (product_id, review_count, rating_sum, created_at, "
            + "updated_at) values (?, 1, 4, now(), now())", PRODUCT);
        double confirmed = counter("kidly.community.moderation.decisions", "by", "admin", "status", "confirmed_hidden");
        double restored = counter("kidly.community.moderation.decisions", "by", "admin", "status", "restored");

        adminService.changeVisibility(99L, id, "HIDDEN");
        adminService.changeVisibility(99L, id, "HIDDEN");
        adminService.changeVisibility(99L, id, "VISIBLE");
        adminService.changeVisibility(99L, id, "VISIBLE");

        assertThat(rows("MODERATION_DECIDED").stream().map(row -> attributes(row).get("status")).toList())
            .containsExactly("CONFIRMED_HIDDEN", "RESTORED");
        assertThat(rows("MODERATION_DECIDED")).allSatisfy(row ->
            assertThat(attributes(row)).containsEntry("by", "admin"));
        assertThat(counter("kidly.community.moderation.decisions", "by", "admin", "status", "confirmed_hidden"))
            .isEqualTo(confirmed + 1);
        assertThat(counter("kidly.community.moderation.decisions", "by", "admin", "status", "restored"))
            .isEqualTo(restored + 1);
    }

    @Test
    @DisplayName("원래 트랜잭션이 롤백되면 활동 행도 카운터도 남지 않는다(찜 수 행이 없어 취소가 실패)")
    void rollbackLeavesNothing() {
        favoriteService.add(ME, String.valueOf(PRODUCT));
        jdbcTemplate.update("delete from community.favorite_stats");
        double removed = counter("kidly.community.favorites", "action", "removed", "cause", "user");

        assertThatThrownBy(() -> favoriteService.remove(ME, String.valueOf(PRODUCT)))
            .isInstanceOf(IllegalStateException.class);

        assertThat(rows("FAVORITE_REMOVED")).isEmpty();
        assertThat(jdbcTemplate.queryForObject("select count(*) from community.favorite", Long.class)).isEqualTo(1L);
        assertThat(counter("kidly.community.favorites", "action", "removed", "cause", "user")).isEqualTo(removed);
    }

    @Test
    @DisplayName("기록한 뒤 트랜잭션이 롤백되면 행도 없고 카운터도 오르지 않는다(카운터는 커밋 뒤에만)")
    void counterOnlyAfterCommit() {
        double added = counter("kidly.community.favorites", "action", "added", "cause", "none");

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            activityRecorder.record(new ActivityRecorder.FavoriteAdded(1L, PRODUCT));
            throw new IllegalStateException("기록 뒤 실패");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(jdbcTemplate.queryForObject("select count(*) from community.activity", Long.class)).isZero();
        assertThat(counter("kidly.community.favorites", "action", "added", "cause", "none")).isEqualTo(added);

        transactionTemplate.executeWithoutResult(status ->
            activityRecorder.record(new ActivityRecorder.FavoriteAdded(1L, PRODUCT)));
        assertThat(counter("kidly.community.favorites", "action", "added", "cause", "none")).isEqualTo(added + 1);
    }

    @Test
    @DisplayName("트랜잭션 밖에서 기록을 부르면 실패한다(MANDATORY)")
    void mandatoryTransaction() {
        assertThatThrownBy(() -> activityRecorder.record(new ActivityRecorder.FavoriteAdded(1L, PRODUCT)))
            .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(jdbcTemplate.queryForObject("select count(*) from community.activity", Long.class)).isZero();
    }

    private long review(long orderItemId, int attempts, long contentVersion) {
        jdbcTemplate.update("insert into community.review (order_item_id, product_id, member_id, nickname, rating, "
            + "content, created_at, updated_at, moderation_status, moderation_attempts, content_version) "
            + "values (?, ?, ?, '닉', 4, '본문', now(), now(), 'PENDING', ?, ?)", orderItemId, PRODUCT, ME, attempts,
            contentVersion);
        return jdbcTemplate.queryForObject("select id from community.review where order_item_id = ?", Long.class,
            orderItemId);
    }

    private static Candidate candidate(long reviewId, int attempts) {
        return new Candidate(reviewId, 0, attempts, new Content("본문", List.of()));
    }

    private static PurchaseConfirmed confirmed(long orderItemId) {
        return new PurchaseConfirmed(orderItemId, 12L, ME, PRODUCT, 15L, null,
            LocalDateTime.of(2026, 11, 10, 10, 0, 0, 123_456_123));
    }

    private String own(int index) {
        String url = imageStorage.ownImageUrlPrefix(ME) + String.format("0b6f3c1e-8a2d-4c5b-9e7f-%012d", index)
            + ".jpg";
        uploadChecker.markUploaded(url);
        return url;
    }

    private List<Map<String, Object>> rows(String type) {
        return jdbcTemplate.queryForList("select product_id, aggregate_id, attributes::text as attributes "
            + "from community.activity where type = ? order by id", type);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> attributes(Map<String, Object> row) {
        return JSON.readValue((String) row.get("attributes"), Map.class);
    }

    private double counter(String name, String... tags) {
        return registry.get(name).tags(tags).counter().count();
    }
}
