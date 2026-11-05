package com.bbcc.kidly.boundedcontext.review.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.favorite.app.FavoriteService;
import com.bbcc.kidly.boundedcontext.review.app.port.in.MemberWithdrawalRecorder;
import com.bbcc.kidly.boundedcontext.support.IntegrationTest;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.member.event.MemberWithdrawnEvent;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 회원 탈퇴 반영(Spring 이벤트 경로): 그 회원의 찜은 모두 지우고 찜 수를 줄이며, 리뷰는 남기고 작성자 탈퇴를 표시한다.
 * 다른 회원 데이터 · 리뷰 집계는 그대로이고, 같은 이벤트를 두 번 받아도 1회분이다.
 */
@IntegrationTest
class MemberWithdrawalIntegrationTest {

    private static final long A = 1L;
    private static final long B = 2L;
    // 나노초가 있는 고정 탈퇴 시각(저장은 마이크로초)
    private static final LocalDateTime WITHDRAWN_AT = LocalDateTime.of(2026, 11, 2, 10, 0, 0, 123_456_123);
    private static final LocalDateTime STORED_AT = LocalDateTime.of(2026, 11, 2, 10, 0, 0, 123_456_000);

    @Autowired
    private EventPublisher eventPublisher;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private FavoriteService favoriteService;

    @Autowired
    private ReviewService reviewService;

    @Autowired
    private MemberWithdrawalRecorder recorder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void data() {
        for (long product = 14; product <= 16; product++) {
            jdbcTemplate.update("insert into community.replica_product (product_id, name, thumbnail_url, "
                + "display_status, source_occurred_at, created_at, updated_at) "
                + "values (?, '상품', null, 'ON_SALE', now(), now(), now())", product);
        }
        favoriteService.add(A, "14");
        favoriteService.add(A, "15");
        favoriteService.add(A, "16");
        favoriteService.add(B, "14");
        eligible(101, A, 14);
        eligible(102, A, 15);
        eligible(103, B, 14);
        reviewService.write(A, "A닉", "101", 5, "A 리뷰 1");
        reviewService.write(A, "A닉", "102", 3, "A 리뷰 2");
        reviewService.write(B, "B닉", "103", 4, "B 리뷰");
    }

    @Test
    @DisplayName("탈퇴하면 그 회원 찜 0 · 찜 수 = 남은 행, 리뷰는 남고 닉네임 null · 탈퇴 시각, 다른 회원 · 리뷰 집계는 그대로")
    void withdraw() {
        List<Map<String, Object>> reviewStatsBefore = reviewStats();

        deliver(withdrawn(A));

        assertThat(count("select count(*) from community.favorite where member_id = ?", A)).isZero();
        assertThat(count("select count(*) from community.favorite where member_id = ?", B)).isEqualTo(1);
        assertFavoriteCountsMatchRows();
        assertThat(jdbcTemplate.queryForList("select nickname, author_withdrawn_at, content from community.review "
            + "where member_id = ? order by order_item_id", A)).hasSize(2).allSatisfy(row -> {
                assertThat(row.get("nickname")).isNull();
                assertThat(((java.sql.Timestamp) row.get("author_withdrawn_at")).toLocalDateTime())
                    .isEqualTo(STORED_AT);
            });
        assertThat(jdbcTemplate.queryForMap("select nickname, author_withdrawn_at from community.review "
            + "where member_id = ?", B)).containsEntry("nickname", "B닉").containsEntry("author_withdrawn_at", null);
        assertThat(reviewStats()).isEqualTo(reviewStatsBefore);
    }

    @Test
    @DisplayName("탈퇴 표시는 리뷰 수정 시각(updated_at)을 바꾸지 않는다(응답 updatedAt은 리뷰 내용 수정 시각)")
    void withdrawalKeepsUpdatedAt() {
        List<Map<String, Object>> before = jdbcTemplate.queryForList("select id, updated_at from community.review "
            + "where member_id = ? order by id", A);

        deliver(withdrawn(A));

        assertThat(jdbcTemplate.queryForList("select id, updated_at from community.review where member_id = ? "
            + "order by id", A)).isEqualTo(before);
    }

    @Test
    @DisplayName("같은 탈퇴를 두 번 받아도 1회분: 찜 수가 두 번 줄지 않고 리뷰 탈퇴 시각 · 수정 시각 그대로")
    void duplicateAppliedOnce() {
        deliver(withdrawn(A));
        List<Map<String, Object>> reviews = jdbcTemplate.queryForList("select id, nickname, author_withdrawn_at, "
            + "updated_at from community.review where member_id = ? order by id", A);
        List<Map<String, Object>> favoriteStats = favoriteStats();

        deliver(withdrawn(A));

        assertThat(jdbcTemplate.queryForList("select id, nickname, author_withdrawn_at, updated_at "
            + "from community.review where member_id = ? order by id", A)).isEqualTo(reviews);
        assertThat(favoriteStats()).isEqualTo(favoriteStats);
        assertFavoriteCountsMatchRows();
    }

    @Test
    @DisplayName("탈퇴 처리와 같은 회원의 찜 해제가 동시에 겹쳐도 찜 수 = 행 수이고 기술 오류가 없다(라운드 반복)")
    void withdrawalAndRemoveConcurrently() throws Exception {
        for (int round = 0; round < 5; round++) {
            long member = 100L + round;
            favoriteService.add(member, "14");
            favoriteService.add(member, "15");

            List<Object> results = runConcurrently(List.of(
                () -> {
                    recorder.record(member, WITHDRAWN_AT);
                    return "withdrawn";
                },
                () -> {
                    favoriteService.remove(member, "14");
                    return "removed";
                }));

            assertThat(results).noneMatch(Throwable.class::isInstance);
            assertThat(count("select count(*) from community.favorite where member_id = ?", member)).isZero();
            assertFavoriteCountsMatchRows();
        }
    }

    @Test
    @DisplayName("탈퇴 처리와 같은 회원의 새 찜이 동시에 겹쳐도 찜 수 = 행 수이고 기술 오류가 없다(라운드 반복)")
    void withdrawalAndAddConcurrently() throws Exception {
        for (int round = 0; round < 5; round++) {
            long member = 200L + round;
            favoriteService.add(member, "14");

            List<Object> results = runConcurrently(List.of(
                () -> {
                    recorder.record(member, WITHDRAWN_AT);
                    return "withdrawn";
                },
                () -> {
                    favoriteService.add(member, "15");
                    return "added";
                }));

            assertThat(results).noneMatch(Throwable.class::isInstance);
            assertThat(count("select count(*) from community.favorite where member_id = ?", member)).isBetween(0, 1);
            assertFavoriteCountsMatchRows();
        }
    }

    private void assertFavoriteCountsMatchRows() {
        List<Map<String, Object>> mismatched = jdbcTemplate.queryForList("""
            select s.product_id from community.favorite_stats s
            where s.favorite_count <> (select count(*) from community.favorite f where f.product_id = s.product_id)
            """);
        assertThat(mismatched).isEmpty();
    }

    private List<Map<String, Object>> reviewStats() {
        return jdbcTemplate.queryForList("select product_id, review_count, rating_sum from community.review_stats "
            + "order by product_id");
    }

    private List<Map<String, Object>> favoriteStats() {
        return jdbcTemplate.queryForList("select product_id, favorite_count, updated_at from community.favorite_stats "
            + "order by product_id");
    }

    private int count(String sql, long memberId) {
        return jdbcTemplate.queryForObject(sql, Integer.class, memberId);
    }

    private void eligible(long orderItemId, long memberId, long productId) {
        jdbcTemplate.update("insert into community.review_eligibility (order_item_id, order_id, member_id, product_id, "
            + "option_id, size, confirmed_at, created_at, updated_at) "
            + "values (?, 1, ?, ?, 1, null, now(), now(), now())", orderItemId, memberId, productId);
    }

    private void deliver(MemberWithdrawnEvent event) {
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(event));
    }

    private static MemberWithdrawnEvent withdrawn(long memberId) {
        return new MemberWithdrawnEvent(UUID.randomUUID(), WITHDRAWN_AT, new MemberWithdrawnEvent.Data(memberId));
    }

    private static List<Object> runConcurrently(List<Callable<Object>> tasks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<Object> task : tasks) {
                futures.add(executor.submit(() -> {
                    start.await();
                    try {
                        return task.call();
                    } catch (Exception e) {
                        return e;
                    }
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
}
