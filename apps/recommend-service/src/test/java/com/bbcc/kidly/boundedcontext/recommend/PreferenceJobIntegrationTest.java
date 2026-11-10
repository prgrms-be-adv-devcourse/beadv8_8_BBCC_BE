package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.app.preference.PreferenceJobResult;
import com.bbcc.kidly.boundedcontext.recommend.app.preference.PreferenceJobService;
import com.bbcc.kidly.boundedcontext.recommend.domain.activity.ActivityType;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.LongStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 선호 Job을 실제 PostgreSQL과 Spring Batch 저장소로 실행해 대상 · 저장 · 다시 실행 · 정리를 확인한다.
 */
@IntegrationTest
class PreferenceJobIntegrationTest {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");
    private static final Long MEMBER_ID = 1L;
    private static final Long CHILD_ID = 1L;
    private static final Long MEMBER_ONLY_ID = 7L;

    @Autowired
    private PreferenceJobService service;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("PREF-11 오늘 기준일로 실행하면 31일 안에 활동한 자녀 · 회원만 배치 기준 시각으로 저장한다")
    void calculateChildAndMemberPreferences() {
        LocalDateTime now = now();
        PreferenceTestData.insertProduct(jdbc, 100L, 10L, "본브랜드", PreferenceTestData.unitVector(0, 1f));
        PreferenceTestData.insertProduct(jdbc, 103L, 13L, "구매브랜드", null);
        childActivity(ActivityType.PRODUCT_VIEWED, CHILD_ID, now.minusDays(1), 100L, 10L, "본브랜드", null);
        childActivity(ActivityType.CART_ITEM_ADDED, CHILD_ID, now.minusDays(2), 101L, 11L, "뺀브랜드", 501L);
        childActivity(ActivityType.CART_ITEM_REMOVED, CHILD_ID, now.minusHours(1), 101L, 11L, "뺀브랜드", 501L);
        childActivity(ActivityType.CART_ITEM_ADDED, CHILD_ID, now.minusDays(2), 102L, 12L, "나중브랜드", 502L);
        childActivity(ActivityType.CART_ITEM_REMOVED, CHILD_ID, now.plusDays(1), 102L, 12L, "나중브랜드", 502L);
        PreferenceTestData.insertPurchase(jdbc, CHILD_ID, 103L, now.minusDays(100));
        childActivity(ActivityType.PRODUCT_VIEWED, 2L, now.minusDays(40), 100L, 10L, "본브랜드", null);
        PreferenceTestData.insertActivity(jdbc, ActivityType.PRODUCT_VIEWED, MEMBER_ONLY_ID, null, now.minusDays(2),
            104L, 14L, "회원브랜드", null);

        LocalDateTime before = now().truncatedTo(ChronoUnit.SECONDS);

        assertThat(service.run(today())).isEqualTo(PreferenceJobResult.COMPLETED);

        assertThat(calculatedAts()).singleElement()
            .satisfies(baseTime -> assertThat(baseTime).isBetween(before, now()));
        assertThat(ids("SELECT category_id FROM recommend.child_category_affinity WHERE child_profile_id = 1"))
            .containsExactlyInAnyOrder(10L, 12L);
        assertThat(strings("SELECT brand FROM recommend.child_brand_affinity WHERE child_profile_id = 1"))
            .containsExactlyInAnyOrder("본브랜드", "나중브랜드", "구매브랜드");
        assertThat(ids("SELECT child_profile_id FROM recommend.child_taste_vector")).containsExactly(CHILD_ID);
        assertThat(ids("SELECT child_profile_id FROM recommend.child_category_affinity WHERE child_profile_id = 2"))
            .isEmpty();
        assertThat(ids("SELECT DISTINCT member_id FROM recommend.member_category_affinity"))
            .containsExactly(MEMBER_ONLY_ID);
        assertThat(ids("SELECT category_id FROM recommend.member_category_affinity")).containsExactly(14L);
    }

    @Test
    @DisplayName("PREF-11 같은 기준일을 다시 실행하면 ALREADY_COMPLETED이고 실행 기록이 늘지 않는다")
    void secondRunIsAlreadyCompleted() {
        assertThat(service.run(today())).isEqualTo(PreferenceJobResult.COMPLETED);

        assertThat(service.run(today())).isEqualTo(PreferenceJobResult.ALREADY_COMPLETED);
        assertThat(executionCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("PREF-11 두 서버가 동시에 같은 기준일을 실행해도 실행은 하나만 생기고 COMPLETED다")
    void concurrentRunsCreateOneExecution() throws Exception {
        childActivity(ActivityType.PRODUCT_VIEWED, CHILD_ID, now().minusDays(1), 100L, 10L, "본브랜드", null);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<PreferenceJobResult> first = executor.submit(() -> {
                start.await();
                return service.run(today());
            });
            Future<PreferenceJobResult> second = executor.submit(() -> {
                start.await();
                return service.run(today());
            });
            start.countDown();

            List<PreferenceJobResult> results = List.of(first.get(), second.get());
            assertThat(results).containsOnlyOnce(PreferenceJobResult.COMPLETED);
            assertThat(results).allMatch(result -> result == PreferenceJobResult.COMPLETED
                || result == PreferenceJobResult.LOCKED || result == PreferenceJobResult.ALREADY_COMPLETED);
        } finally {
            executor.shutdownNow();
        }
        assertThat(strings("SELECT status FROM recommend.batch_job_execution")).containsExactly("COMPLETED");
    }

    @Test
    @DisplayName("PREF-11 실패한 chunk는 롤백되고, 다시 실행하면 첫 실행의 기준 시각으로 실패한 chunk부터 이어서 처리한다")
    void restartFromFailedChunk() {
        PreferenceJobTestData.insertChildrenViewed(jdbc, 150, now().minusDays(1));

        assertThat(runFailingInsertsInto("child_category_affinity", "NEW.child_profile_id = 120"))
            .isEqualTo(PreferenceJobResult.FAILED);
        assertThat(categoryChildIds()).containsExactlyElementsOf(range(1, 100));
        LocalDateTime firstBaseTime = calculatedAts().getFirst();

        assertThat(service.run(today())).isEqualTo(PreferenceJobResult.COMPLETED);

        assertThat(jdbc.queryForObject("""
            SELECT read_count FROM recommend.batch_step_execution WHERE step_name = 'childPreferenceStep'
            ORDER BY step_execution_id DESC LIMIT 1
            """, Long.class)).isEqualTo(50L);
        assertThat(categoryChildIds()).containsExactlyElementsOf(range(1, 150));
        assertThat(calculatedAts()).containsExactly(firstBaseTime);
    }

    @Test
    @DisplayName("PREF-11 서버가 자녀 Step 도중 죽어 Job 실행 문맥에 기준 시각이 없어도, 다시 실행하면 첫 실행의 기준 시각을 쓰고 끝난 chunk의 줄을 남긴다")
    void restartAfterCrashKeepsFirstBaseTime() throws InterruptedException {
        PreferenceJobTestData.insertChildrenViewed(jdbc, 150, now().minusDays(1));
        assertThat(runFailingInsertsInto("child_category_affinity", "NEW.child_profile_id = 120"))
            .isEqualTo(PreferenceJobResult.FAILED);
        LocalDateTime firstBaseTime = calculatedAts().getFirst();
        PreferenceJobTestData.markExecutionsRunning(jdbc, LocalDateTime.now().minusMinutes(31));
        // 첫 실행의 Job 실행 문맥은 빈 채로 저장되고 Step이 끝나야 다시 저장되므로, Step 도중 죽으면 비어 있다
        jdbc.update("DELETE FROM recommend.batch_job_execution_context");
        waitForNextSecond(firstBaseTime);

        assertThat(service.run(today())).isEqualTo(PreferenceJobResult.COMPLETED);

        assertThat(calculatedAts()).containsExactly(firstBaseTime);
        assertThat(categoryChildIds()).containsExactlyElementsOf(range(1, 150));
    }

    @Test
    @DisplayName("PREF-11 회원 Step이 실패한 뒤 다시 실행하면 자녀 Step은 다시 돌지 않고 정리 Step이 자녀 줄을 지우지 않는다")
    void restartFromFailedMemberStep() throws InterruptedException {
        childActivity(ActivityType.PRODUCT_VIEWED, CHILD_ID, now().minusDays(1), 100L, 10L, "본브랜드", null);
        PreferenceTestData.insertActivity(jdbc, ActivityType.PRODUCT_VIEWED, MEMBER_ONLY_ID, null,
            now().minusDays(2), 104L, 14L, "회원브랜드", null);
        assertThat(runFailingInsertsInto("member_category_affinity", "true")).isEqualTo(PreferenceJobResult.FAILED);
        LocalDateTime firstBaseTime = calculatedAts().getFirst();
        waitForNextSecond(firstBaseTime);

        assertThat(service.run(today())).isEqualTo(PreferenceJobResult.COMPLETED);

        assertThat(jdbc.queryForList("""
            SELECT read_count FROM recommend.batch_step_execution WHERE step_name = 'childPreferenceStep'
            AND job_execution_id = (SELECT max(job_execution_id) FROM recommend.batch_job_execution)
            """, Long.class)).allMatch(readCount -> readCount == 0L);
        assertThat(ids("SELECT child_profile_id FROM recommend.child_category_affinity")).containsExactly(CHILD_ID);
        assertThat(ids("SELECT member_id FROM recommend.member_category_affinity")).containsExactly(MEMBER_ONLY_ID);
        assertThat(calculatedAts()).containsExactly(firstBaseTime);
    }

    @Test
    @DisplayName("PREF-11 이번 실행이 다시 쓰지 않은 탈퇴 회원 · 삭제 자녀 · 활동이 끊긴 자녀의 줄은 지우고 지금 대상의 줄은 남긴다")
    void cleanUpRowsNotRewritten() {
        LocalDateTime now = now();
        LocalDateTime yesterday = now.minusDays(1);
        childActivity(ActivityType.PRODUCT_VIEWED, CHILD_ID, yesterday, 100L, 10L, "본브랜드", null);
        PreferenceTestData.insertChildCategory(jdbc, CHILD_ID, 99L, yesterday);
        PreferenceTestData.insertMemberCategory(jdbc, 8L, 10L, yesterday);
        childActivity(ActivityType.PRODUCT_VIEWED, 20L, yesterday, 100L, 10L, "본브랜드", null);
        PreferenceTestData.insertReplicaChild(jdbc, 20L, yesterday);
        PreferenceTestData.insertChildCategory(jdbc, 20L, 10L, yesterday);
        childActivity(ActivityType.PRODUCT_VIEWED, 21L, now.minusDays(40), 100L, 10L, "본브랜드", null);
        PreferenceTestData.insertChildCategory(jdbc, 21L, 10L, yesterday);

        assertThat(service.run(today())).isEqualTo(PreferenceJobResult.COMPLETED);

        assertThat(ids("SELECT DISTINCT child_profile_id FROM recommend.child_category_affinity"))
            .containsExactly(CHILD_ID);
        assertThat(ids("SELECT category_id FROM recommend.child_category_affinity")).containsExactly(10L);
        assertThat(ids("SELECT member_id FROM recommend.member_category_affinity")).isEmpty();
    }

    @Test
    @DisplayName("PREF-11 30분 넘게 진행이 없는 실행 중 기록은 FAILED로 닫고 같은 기준일을 실행한다")
    void staleRunningExecutionIsRecovered() {
        leaveRunningExecution(LocalDateTime.now().minusMinutes(31));

        assertThat(service.run(today())).isEqualTo(PreferenceJobResult.COMPLETED);

        assertThat(strings("SELECT status FROM recommend.batch_job_execution ORDER BY job_execution_id"))
            .containsExactly("FAILED", "COMPLETED");
    }

    @Test
    @DisplayName("PREF-11 최근에 진행한 실행 중 기록이 있으면 IN_PROGRESS이고 새 실행을 만들지 않는다")
    void recentRunningExecutionIsInProgress() {
        leaveRunningExecution(LocalDateTime.now().minusMinutes(1));

        assertThat(service.run(today())).isEqualTo(PreferenceJobResult.IN_PROGRESS);

        assertThat(strings("SELECT status FROM recommend.batch_job_execution")).containsExactly("STARTED");
    }

    @Test
    @DisplayName("Spring Batch 실행 기록은 recommend 스키마에만 남고 public 스키마에는 BATCH 표가 없다")
    void batchTablesLiveInRecommendSchema() {
        assertThat(service.run(today())).isEqualTo(PreferenceJobResult.COMPLETED);

        assertThat(executionCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
            SELECT count(*) FROM information_schema.tables
            WHERE table_schema = 'public' AND table_name LIKE 'batch\\_%'
            """, Integer.class)).isZero();
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(SERVICE_ZONE);
    }

    private static LocalDate today() {
        return LocalDate.now(SERVICE_ZONE);
    }

    private void childActivity(ActivityType type, Long childProfileId, LocalDateTime occurredAt, Long productId,
        Long categoryId, String brand, Long cartItemId) {
        PreferenceTestData.insertActivity(jdbc, type, MEMBER_ID, childProfileId, occurredAt, productId, categoryId,
            brand, cartItemId);
    }

    // 오늘 기준일을 한 번 실행한 뒤 서버가 자녀 Step 도중 죽은 것처럼 되돌린다
    private void leaveRunningExecution(LocalDateTime lastActivity) {
        assertThat(service.run(today())).isEqualTo(PreferenceJobResult.COMPLETED);
        PreferenceJobTestData.markExecutionsRunning(jdbc, lastActivity);
    }

    // 트리거로 조건에 맞는 줄의 저장을 실패시킨 채 실행한다. 트리거는 결과와 상관없이 지운다
    private PreferenceJobResult runFailingInsertsInto(String table, String condition) {
        try {
            PreferenceJobTestData.failInserts(jdbc, table, condition);
            return service.run(today());
        } finally {
            PreferenceJobTestData.stopFailingInserts(jdbc, table);
        }
    }

    // 기준 시각은 초 단위라 같은 초에 다시 실행하면 새로 정한 시각이 첫 시각과 같아 구별되지 않는다
    private static void waitForNextSecond(LocalDateTime baseTime) throws InterruptedException {
        while (!now().truncatedTo(ChronoUnit.SECONDS).isAfter(baseTime)) {
            Thread.sleep(50);
        }
    }

    private Integer executionCount() {
        return jdbc.queryForObject("SELECT count(*) FROM recommend.batch_job_execution", Integer.class);
    }

    private List<Long> categoryChildIds() {
        return ids("SELECT DISTINCT child_profile_id FROM recommend.child_category_affinity ORDER BY 1");
    }

    private List<LocalDateTime> calculatedAts() {
        return jdbc.queryForList("""
            SELECT calculated_at FROM recommend.child_category_affinity
            UNION SELECT calculated_at FROM recommend.child_brand_affinity
            UNION SELECT calculated_at FROM recommend.child_taste_vector
            UNION SELECT calculated_at FROM recommend.member_category_affinity
            UNION SELECT calculated_at FROM recommend.member_brand_affinity
            UNION SELECT calculated_at FROM recommend.member_taste_vector
            ORDER BY 1
            """, LocalDateTime.class);
    }

    private static List<Long> range(long from, long to) {
        return LongStream.rangeClosed(from, to).boxed().toList();
    }

    private List<Long> ids(String sql) {
        return jdbc.queryForList(sql, Long.class);
    }

    private List<String> strings(String sql) {
        return jdbc.queryForList(sql, String.class);
    }
}
