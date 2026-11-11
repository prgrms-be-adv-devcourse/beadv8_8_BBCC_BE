package com.bbcc.kidly.boundedcontext.review.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.support.AsyncWait;
import com.bbcc.kidly.boundedcontext.support.IntegrationTest;
import com.bbcc.kidly.global.error.BusinessException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 리뷰 작성의 동시성: 같은 주문상품 동시 작성은 1개만 남고 나머지는 이미 쓴 리뷰(409)로 끝난다.
 * 같은 상품의 서로 다른 주문상품에 동시에 작성 · 수정 · 삭제해도 집계는 실제 리뷰 행의 합계와 같다(원자적 증감).
 */
@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class ReviewConcurrencyIntegrationTest {

    private static final long ME = 13L;
    private static final long PRODUCT = 14L;
    // 동시 실행이 겹치는 경우를 여러 번 만들기 위한 반복 횟수
    private static final int ROUNDS = 5;
    private static final String NICKNAME = "닉네임_" + UUID.randomUUID();
    private static final String CONTENT = "본문_" + UUID.randomUUID();

    @Autowired
    private ReviewService reviewService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("같은 주문상품에 동시에 2번 쓰면 리뷰 1개 · 하나는 REVIEW_ALREADY_WRITTEN이고, 로그에 본문 · 닉네임이 없다")
    void sameOrderItemConcurrentWrites(CapturedOutput output) throws Exception {
        eligible(11);
        List<Object> results = runConcurrently(2, i -> () -> reviewService.write(ME, NICKNAME, "11", 5, CONTENT));

        assertThat(results).filteredOn(ReviewService.ReviewView.class::isInstance).hasSize(1);
        assertThat(results).filteredOn(BusinessException.class::isInstance).singleElement()
            .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                .isEqualTo(ReviewErrorCode.REVIEW_ALREADY_WRITTEN));
        assertThat(reviewCount(11)).isEqualTo(1);
        assertThat(stats()).containsEntry("review_count", 1).containsEntry("rating_sum", 5);
        assertThat(output.getOut()).doesNotContain(NICKNAME).doesNotContain(CONTENT);
    }

    @Test
    @DisplayName("확인 뒤 저장이 유일 제약에 막혀도(다른 작성이 먼저 커밋) REVIEW_ALREADY_WRITTEN이고 집계 · 로그는 깨끗하다")
    void uniqueViolationBecomesAlreadyWritten(CapturedOutput output) throws Exception {
        eligible(12);
        CountDownLatch inserted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<Void> other = CompletableFuture.runAsync(() -> transactionTemplate.executeWithoutResult(s -> {
            jdbcTemplate.update("insert into community.review (order_item_id, product_id, member_id, nickname, rating, "
                + "content, created_at, updated_at, moderation_status) values (12, ?, ?, '먼저', 3, '먼저 쓴 리뷰', "
                + "now(), now(), 'PASSED')",
                PRODUCT, ME);
            inserted.countDown();
            await(release);
        }));
        CompletableFuture<Object> writing;
        try {
            assertThat(inserted.await(5, TimeUnit.SECONDS)).isTrue();
            writing = CompletableFuture.supplyAsync(() -> attempt(
                () -> reviewService.write(ME, NICKNAME, "12", 5, CONTENT)));
            AsyncWait.until(Duration.ofSeconds(5), this::reviewInsertWaitingForLock);
        } finally {
            release.countDown();
            other.get(10, TimeUnit.SECONDS);
        }

        assertThat(writing.get(10, TimeUnit.SECONDS)).isInstanceOfSatisfying(BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ReviewErrorCode.REVIEW_ALREADY_WRITTEN));
        assertThat(reviewCount(12)).isEqualTo(1);
        assertThat(stats()).isNull();
        assertThat(output.getOut()).doesNotContain(NICKNAME).doesNotContain(CONTENT);
    }

    @Test
    @DisplayName("같은 상품의 서로 다른 주문상품에 동시에 작성 · 수정 · 삭제해도 집계 = 실제 리뷰 행 합계")
    void statsMatchRowsUnderConcurrency() throws Exception {
        int existing = 6;
        int fresh = 6;
        List<Long> existingIds = new ArrayList<>();
        for (int i = 0; i < existing + fresh; i++) {
            eligible(100 + i);
        }
        for (int i = 0; i < existing; i++) {
            existingIds.add(reviewService.write(ME, "닉", String.valueOf(100 + i), 3, "기존").reviewId());
        }

        List<Object> results = runConcurrently(existing + fresh, i -> {
            if (i >= existing) {
                return () -> reviewService.write(ME, "닉", String.valueOf(100 + i), 1 + i % 5, "새 리뷰");
            }
            long id = existingIds.get(i);
            if (i % 2 == 0) {
                return () -> reviewService.update(ME, id, 5, "고침");
            }
            return () -> {
                reviewService.delete(ME, id);
                return "deleted";
            };
        });

        assertThat(results).noneMatch(Throwable.class::isInstance);
        Map<String, Object> actual = jdbcTemplate.queryForMap("select count(*)::int as review_count, "
            + "coalesce(sum(rating), 0)::int as rating_sum from community.review where product_id = ?", PRODUCT);
        assertThat(stats()).containsEntry("review_count", actual.get("review_count"))
            .containsEntry("rating_sum", actual.get("rating_sum"));
        assertThat(actual.get("review_count")).isEqualTo(existing / 2 + fresh);
    }

    @Test
    @DisplayName("같은 리뷰를 서로 다른 별점으로 동시에 여러 번 고쳐도 집계 별점 합 = 실제 행 별점(라운드 반복)")
    void sameReviewConcurrentUpdates() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            long reviewId = writeReview(300 + round, 3);

            Outcomes outcomes = Outcomes.of(runConcurrently(6,
                i -> () -> reviewService.update(ME, reviewId, 1 + i % 5, "동시 수정 " + i)));

            assertThat(outcomes.technical()).as("기술 오류").isEmpty();
            assertThat(outcomes.success()).isEqualTo(6);
            assertStatsMatchRows();
        }
    }

    @Test
    @DisplayName("같은 리뷰를 동시에 고치고 지우면 수정은 성공 또는 404, 삭제는 성공이고 집계 = 행 합계(라운드 반복)")
    void sameReviewConcurrentUpdateAndDelete() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            long reviewId = writeReview(400 + round, 3);

            Outcomes outcomes = Outcomes.of(runConcurrently(2, i -> i == 0
                ? () -> reviewService.update(ME, reviewId, 5, "동시 수정")
                : () -> {
                    reviewService.delete(ME, reviewId);
                    return "deleted";
                }));

            assertThat(outcomes.technical()).as("기술 오류").isEmpty();
            assertThat(outcomes.success() + outcomes.rejected(ReviewErrorCode.REVIEW_NOT_FOUND)).isEqualTo(2);
            assertThat(reviewCount(400 + round)).isZero();
            assertStatsMatchRows();
        }
    }

    @Test
    @DisplayName("같은 리뷰를 동시에 4번 지우면 1번만 성공하고 나머지는 404, 집계는 −1 한 번(라운드 반복)")
    void sameReviewConcurrentDeletes() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            long reviewId = writeReview(500 + round, 4);

            Outcomes outcomes = Outcomes.of(runConcurrently(4, i -> () -> {
                reviewService.delete(ME, reviewId);
                return "deleted";
            }));

            assertThat(outcomes.technical()).as("기술 오류").isEmpty();
            assertThat(outcomes.success()).isEqualTo(1);
            assertThat(outcomes.rejected(ReviewErrorCode.REVIEW_NOT_FOUND)).isEqualTo(3);
            assertStatsMatchRows();
        }
    }

    @Test
    @DisplayName("삭제와 같은 주문상품 재작성이 동시면 리뷰는 0~1개이고 집계 = 행 합계(라운드 반복)")
    void deleteAndRewriteConcurrently() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            long orderItemId = 600 + round;
            long reviewId = writeReview(orderItemId, 2);

            Outcomes outcomes = Outcomes.of(runConcurrently(2, i -> i == 0
                ? () -> {
                    reviewService.delete(ME, reviewId);
                    return "deleted";
                }
                : () -> reviewService.write(ME, "닉", String.valueOf(orderItemId), 5, "다시 씀")));

            assertThat(outcomes.technical()).as("기술 오류").isEmpty();
            assertThat(outcomes.success() + outcomes.rejected(ReviewErrorCode.REVIEW_ALREADY_WRITTEN)).isEqualTo(2);
            assertThat(reviewCount(orderItemId)).isBetween(0, 1);
            assertStatsMatchRows();
        }
    }

    @Test
    @DisplayName("핏 동시성: 여러 회원 동시 작성(핏 섞음) · 같은 리뷰 동시 핏 변경 · 핏 변경 + 삭제 동시 뒤 fit_* = 행별 핏 개수")
    void fitCountsUnderConcurrency() throws Exception {
        String[] fits = {"SMALL", "TRUE_TO_SIZE", "LARGE"};
        List<Object> writes = runConcurrently(9, i -> {
            sizedEligible(700 + i, 100L + i);
            return () -> reviewService.write(100L + i, "닉", String.valueOf(700 + i), 4, "본문",
                ReviewService.FitInput.text(fits[i % 3]));
        });
        assertThat(Outcomes.of(writes).technical()).isEmpty();
        assertFitsMatchRows();

        for (int round = 0; round < ROUNDS; round++) {
            sizedEligible(800 + round, ME);
            long reviewId = reviewService.write(ME, "닉", String.valueOf(800 + round), 3, "처음",
                ReviewService.FitInput.text("SMALL")).reviewId();

            Outcomes changes = Outcomes.of(runConcurrently(6, i -> () -> reviewService.update(ME, reviewId,
                new ReviewService.ReviewChange(false, null, false, null, ReviewService.FitInput.text(fits[i % 3])))));
            assertThat(changes.technical()).as("기술 오류").isEmpty();
            assertFitsMatchRows();

            Outcomes changeAndDelete = Outcomes.of(runConcurrently(2, i -> i == 0
                ? () -> reviewService.update(ME, reviewId, new ReviewService.ReviewChange(false, null, false, null,
                    ReviewService.FitInput.text("LARGE")))
                : () -> {
                    reviewService.delete(ME, reviewId);
                    return "deleted";
                }));
            assertThat(changeAndDelete.technical()).as("기술 오류").isEmpty();
            assertFitsMatchRows();
        }
    }

    private void assertFitsMatchRows() {
        assertThat(jdbcTemplate.queryForList("""
            select s.product_id from community.review_stats s
            where s.fit_small <> (select count(*) from community.review r
                                  where r.product_id = s.product_id and r.fit = 'SMALL')
               or s.fit_true_to_size <> (select count(*) from community.review r
                                         where r.product_id = s.product_id and r.fit = 'TRUE_TO_SIZE')
               or s.fit_large <> (select count(*) from community.review r
                                  where r.product_id = s.product_id and r.fit = 'LARGE')
            """)).isEmpty();
    }

    private void sizedEligible(long orderItemId, long memberId) {
        jdbcTemplate.update("insert into community.review_eligibility (order_item_id, order_id, member_id, product_id, "
            + "option_id, size, confirmed_at, created_at, updated_at) "
            + "values (?, 1, ?, ?, 1, '110', now(), now(), now())", orderItemId, memberId, PRODUCT);
    }

    private long writeReview(long orderItemId, int rating) {
        eligible(orderItemId);
        return reviewService.write(ME, "닉", String.valueOf(orderItemId), rating, "처음").reviewId();
    }

    private void assertStatsMatchRows() {
        Map<String, Object> actual = jdbcTemplate.queryForMap("select count(*)::int as review_count, "
            + "coalesce(sum(rating), 0)::int as rating_sum from community.review where product_id = ?", PRODUCT);
        assertThat(stats()).containsEntry("review_count", actual.get("review_count"))
            .containsEntry("rating_sum", actual.get("rating_sum"));
    }

    /**
     * 동시 실행 결과를 성공 · 업무 거절(BusinessException) · 기술 오류로 나눈다.
     */
    private record Outcomes(int success, List<BusinessException> business, List<Object> technical) {

        static Outcomes of(List<Object> results) {
            int success = 0;
            List<BusinessException> business = new ArrayList<>();
            List<Object> technical = new ArrayList<>();
            for (Object result : results) {
                if (result instanceof BusinessException e) {
                    business.add(e);
                } else if (result instanceof Throwable) {
                    technical.add(result);
                } else {
                    success++;
                }
            }
            assertThat(success + business.size() + technical.size()).isEqualTo(results.size());
            return new Outcomes(success, business, technical);
        }

        int rejected(ReviewErrorCode code) {
            return (int) business.stream().filter(e -> e.getErrorCode() == code).count();
        }
    }

    private List<Object> runConcurrently(int count, java.util.function.IntFunction<Callable<Object>> task)
        throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(count);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                Callable<Object> work = task.apply(i);
                futures.add(executor.submit(() -> {
                    start.await();
                    return attempt(work);
                }));
            }
            start.countDown();
            List<Object> results = new ArrayList<>();
            for (Future<Object> future : futures) {
                results.add(future.get(20, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    private static Object attempt(Callable<Object> work) {
        try {
            return work.call();
        } catch (Exception e) {
            return e;
        }
    }

    private boolean reviewInsertWaitingForLock() {
        Integer waiting = jdbcTemplate.queryForObject(
            "select count(*) from pg_stat_activity where wait_event_type = 'Lock' "
                + "and query like 'insert into community.review %'", Integer.class);
        return waiting != null && waiting > 0;
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private void eligible(long orderItemId) {
        jdbcTemplate.update("insert into community.review_eligibility (order_item_id, order_id, member_id, product_id, "
            + "option_id, size, confirmed_at, created_at, updated_at) "
            + "values (?, 1, ?, ?, 1, null, now(), now(), now())",
            orderItemId, ME, PRODUCT);
    }

    private int reviewCount(long orderItemId) {
        return jdbcTemplate.queryForObject("select count(*) from community.review where order_item_id = ?",
            Integer.class, orderItemId);
    }

    private Map<String, Object> stats() {
        return jdbcTemplate.queryForList("select review_count::int as review_count, rating_sum::int as rating_sum "
            + "from community.review_stats where product_id = ?", PRODUCT).stream().findFirst().orElse(null);
    }
}
