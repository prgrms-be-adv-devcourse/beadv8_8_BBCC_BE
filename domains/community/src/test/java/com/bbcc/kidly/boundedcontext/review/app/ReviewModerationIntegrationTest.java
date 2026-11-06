package com.bbcc.kidly.boundedcontext.review.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.review.app.ReviewModerationRecorder.Recorded;
import com.bbcc.kidly.boundedcontext.review.app.ReviewModerationService.Candidate;
import com.bbcc.kidly.boundedcontext.review.app.ReviewModerationService.ModerationRun;
import com.bbcc.kidly.boundedcontext.review.app.ReviewService.ImageUrlsInput;
import com.bbcc.kidly.boundedcontext.review.app.port.ContentModerator.FailureCause;
import com.bbcc.kidly.boundedcontext.review.app.port.ContentModerator.Result;
import com.bbcc.kidly.boundedcontext.review.app.port.ModerationRunLock;
import com.bbcc.kidly.boundedcontext.review.app.port.ReviewImageStorage;
import com.bbcc.kidly.boundedcontext.review.domain.ModerationReason;
import com.bbcc.kidly.boundedcontext.review.out.moderation.FakeModerationAdapter;
import com.bbcc.kidly.boundedcontext.support.IntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 검사 처리(스케줄러가 부르는 processPending)를 실제 DB로 확인한다. 테스트 설정은 스케줄을 끄고(enabled false) 검사기는
 * 금칙어 → 가짜(fake) 순서이며, 재시도 상한은 3이다.
 */
@IntegrationTest
class ReviewModerationIntegrationTest {

    private static final long ME = 13L;
    private static final long PRODUCT = 14L;

    @Autowired
    private ReviewModerationService moderationService;

    @Autowired
    private ReviewModerationRecorder recorder;

    @Autowired
    private ReviewService reviewService;

    @Autowired
    private ReviewQueryService queryService;

    @Autowired
    private ModerationRunLock runLock;

    @Autowired
    private ReviewImageStorage imageStorage;

    @Autowired
    private FakeModerationAdapter fake;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void reset() {
        fake.reset();
    }

    @Test
    @DisplayName("통과: PENDING → PASSED, 검사 시각 기록, 집계 그대로. 검사기에는 본문과 사진 주소가 간다")
    void passes() {
        String image = imageStorage.ownImageUrlPrefix(ME) + "0b6f3c1e-8a2d-4c5b-9e7f-1a2b3c4d5e6f.jpg";
        long id = reviewService.write(ME, "닉", String.valueOf(eligible(11, ME)), 4, "좋은 옷",
            ReviewService.FitInput.absent(), ImageUrlsInput.of(List.of(image))).reviewId();

        ModerationRun run = moderationService.processPending();

        assertThat(run.passed()).isEqualTo(1);
        assertThat(row(id)).containsEntry("moderation_status", "PASSED").containsEntry("moderation_attempts", 0);
        assertThat(row(id).get("moderated_at")).isNotNull();
        assertThat(fake.calls()).singleElement().satisfies(content -> {
            assertThat(content.text()).isEqualTo("좋은 옷");
            assertThat(content.imageUrls()).containsExactly(image);
        });
        assertThat(queryService.summary(String.valueOf(PRODUCT)).reviewCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("걸림: PENDING → HIDDEN + 이유, 요약(수 · 평균 · 핏)에서 빠진다. 금칙어에 걸리면 AI(fake)는 부르지 않는다")
    void hides() {
        long sized = eligible(11, ME, "110");
        long id = reviewService.write(ME, "닉", String.valueOf(sized), 5, "좋아요",
            ReviewService.FitInput.text("SMALL")).reviewId();
        long banned = reviewService.write(ME + 1, "닉", String.valueOf(eligible(12, ME + 1)), 1, "이건 금 칙 어 입니다")
            .reviewId();
        fake.willReturn(Result.flagged(ModerationReason.HATE));

        ModerationRun run = moderationService.processPending();

        assertThat(run.hidden()).isEqualTo(2);
        assertThat(row(id)).containsEntry("moderation_status", "HIDDEN").containsEntry("moderation_reason", "HATE");
        assertThat(row(banned)).containsEntry("moderation_status", "HIDDEN")
            .containsEntry("moderation_reason", "PROFANITY");
        assertThat(fake.calls()).hasSize(1);
        ReviewQueryService.ProductSummary summary = queryService.summary(String.valueOf(PRODUCT));
        assertThat(summary.reviewCount()).isZero();
        assertThat(summary.fitSummary()).isEqualTo(new ReviewQueryService.FitSummary(0, 0, 0));
    }

    @Test
    @DisplayName("판단 못 함(장애 · 시간 초과): PENDING 그대로 + 시도 +1, 다음 주기에 다시 검사한다")
    void undecidedRetries() {
        long id = write(11);
        fake.willReturn(Result.undecided(FailureCause.UNAVAILABLE), Result.undecided(FailureCause.TIMEOUT),
            Result.passed());

        moderationService.processPending();
        assertThat(row(id)).containsEntry("moderation_status", "PENDING").containsEntry("moderation_attempts", 1);
        moderationService.processPending();
        assertThat(row(id)).containsEntry("moderation_status", "PENDING").containsEntry("moderation_attempts", 2);
        moderationService.processPending();
        assertThat(row(id)).containsEntry("moderation_status", "PASSED");
    }

    @Test
    @DisplayName("한도 초과(429)면 이번 주기의 남은 건은 검사하지 않는다(id 순)")
    void rateLimitStopsBatch() {
        long first = write(11);
        long second = write(12);
        long third = write(13);
        fake.willReturn(Result.passed(), Result.undecided(FailureCause.RATE_LIMITED), Result.passed());

        ModerationRun run = moderationService.processPending();

        assertThat(run.rateLimited()).isTrue();
        assertThat(fake.calls()).hasSize(2);
        assertThat(row(first)).containsEntry("moderation_status", "PASSED");
        // 한도 초과는 검사기 탓이 아니라 시도 횟수를 올리지 않는다
        assertThat(row(second)).containsEntry("moderation_status", "PENDING").containsEntry("moderation_attempts", 0);
        assertThat(row(third)).containsEntry("moderation_status", "PENDING").containsEntry("moderation_attempts", 0);
    }

    @Test
    @DisplayName("한도 초과(429)는 상한(3)보다 많이 받아도 시도 횟수를 올리지 않고 대기 그대로다(일 한도가 바닥나도 검사 없이 빠지지 않는다)")
    void rateLimitKeepsAttempts() {
        long id = write(11);
        fake.willReturn(Result.undecided(FailureCause.RATE_LIMITED));

        for (int i = 0; i < 5; i++) {
            moderationService.processPending();
        }

        assertThat(row(id)).containsEntry("moderation_status", "PENDING").containsEntry("moderation_attempts", 0);
        assertThat(fake.calls()).hasSize(5);
        assertThat(moderationService.pendingBatch()).singleElement()
            .satisfies(candidate -> assertThat(candidate.reviewId()).isEqualTo(id));
    }

    @Test
    @DisplayName("재시도 상한(3)에 닿은 리뷰는 대기 목록에서 빠지고 공개 그대로다")
    void exhaustedAttemptsLeaveQueue() {
        long id = write(11);
        fake.willReturn(Result.undecided(FailureCause.UNAVAILABLE));

        for (int i = 0; i < 5; i++) {
            moderationService.processPending();
        }

        assertThat(row(id)).containsEntry("moderation_status", "PENDING").containsEntry("moderation_attempts", 3);
        assertThat(fake.calls()).hasSize(3);
        assertThat(moderationService.pendingBatch()).isEmpty();
        assertThat(queryService.summary(String.valueOf(PRODUCT)).reviewCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("검사 중에 작성자가 글을 고치면 그 검사 결과는 버리고, 고친 글을 다음 주기에 다시 검사한다")
    void editDuringCheckDiscardsResult() {
        long id = write(11);
        fake.duringNextCall(() -> reviewService.update(ME, id,
            new ReviewService.ReviewChange(false, null, true, "검사 중에 고친 글")));
        fake.willReturn(Result.flagged(ModerationReason.HATE), Result.passed());

        ModerationRun run = moderationService.processPending();

        assertThat(run.discarded()).isEqualTo(1);
        assertThat(row(id)).containsEntry("moderation_status", "PENDING").containsEntry("content_version", 1L);
        assertThat(queryService.summary(String.valueOf(PRODUCT)).reviewCount()).isEqualTo(1);
        moderationService.processPending();
        assertThat(row(id)).containsEntry("moderation_status", "PASSED");
        assertThat(fake.calls().getLast().text()).isEqualTo("검사 중에 고친 글");
    }

    @Test
    @DisplayName("같은 검사 결과를 두 처리기가 동시에 반영해도 한 번만 반영되고 집계도 한 번만 빠진다")
    void concurrentRecordAppliesOnce() throws Exception {
        long id = write(11);
        Candidate candidate = moderationService.pendingBatch().getFirst();
        CyclicBarrier barrier = new CyclicBarrier(2);
        Result flagged = Result.flagged(ModerationReason.SEXUAL);

        CompletableFuture<Recorded> a = CompletableFuture.supplyAsync(() -> recordAfter(barrier, candidate, flagged));
        CompletableFuture<Recorded> b = CompletableFuture.supplyAsync(() -> recordAfter(barrier, candidate, flagged));

        assertThat(List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS)))
            .containsExactlyInAnyOrder(Recorded.HIDDEN, Recorded.DISCARDED);
        assertThat(row(id)).containsEntry("moderation_status", "HIDDEN");
        assertThat(jdbcTemplate.queryForObject("select review_count from community.review_stats where product_id = ?",
            Long.class, PRODUCT)).isZero();
    }

    @Test
    @DisplayName("검사 실행은 서버 한 대에서만: 다른 쪽이 잠금을 쥐고 있으면 이번 주기를 건너뛴다")
    void runLockIsExclusive() throws Exception {
        write(11);
        CountDownLatch holding = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<Boolean> other = CompletableFuture.supplyAsync(() -> runLock.runExclusively(() -> {
            holding.countDown();
            await(release);
        }));
        assertThat(holding.await(5, TimeUnit.SECONDS)).isTrue();

        ModerationRun skipped = moderationService.processPending();
        release.countDown();

        assertThat(other.get(10, TimeUnit.SECONDS)).isTrue();
        assertThat(skipped.skipped()).isTrue();
        assertThat(fake.calls()).isEmpty();
        assertThat(moderationService.processPending().passed()).isEqualTo(1);
    }

    @Test
    @DisplayName("공개 목록은 숨김을 빼고도 상품별 인덱스(product_id, created_at, id)를 쓰고, 대기 조회는 검사 상태 인덱스를 쓴다")
    void queriesUseIndexes() {
        String publicPlan = explain("select r.id from community.review r where r.product_id = 14 "
            + "and r.moderation_status in ('PENDING', 'PASSED', 'RESTORED') "
            + "order by r.created_at desc, r.id desc limit 21");
        String queuePlan = explain("select r.id from community.review r where r.moderation_status = 'PENDING' "
            + "and r.moderation_attempts < 3 order by r.id limit 10");

        assertThat(publicPlan).contains("idx_review_product_created");
        assertThat(queuePlan).contains("idx_review_moderation_status");
    }

    private Recorded recordAfter(CyclicBarrier barrier, Candidate candidate, Result result) {
        try {
            barrier.await(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return recorder.record(candidate, result);
    }

    private String explain(String sql) {
        return transactionTemplate.execute(status -> {
            jdbcTemplate.execute("set local enable_seqscan = off");
            return String.join("\n", jdbcTemplate.queryForList("explain " + sql, String.class));
        });
    }

    private long write(long orderItemId) {
        return reviewService.write(ME, "닉", String.valueOf(eligible(orderItemId, ME)), 4, "좋은 옷").reviewId();
    }

    private long eligible(long orderItemId, long memberId) {
        return eligible(orderItemId, memberId, null);
    }

    private long eligible(long orderItemId, long memberId, String size) {
        jdbcTemplate.update("insert into community.review_eligibility (order_item_id, order_id, member_id, product_id, "
            + "option_id, size, confirmed_at, created_at, updated_at) values (?, 12, ?, ?, 15, ?, now(), now(), now())",
            orderItemId, memberId, PRODUCT, size);
        return orderItemId;
    }

    private Map<String, Object> row(long id) {
        return jdbcTemplate.queryForMap("select moderation_status, moderation_reason, moderation_attempts, "
            + "content_version, moderated_at from community.review where id = ?", id);
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
