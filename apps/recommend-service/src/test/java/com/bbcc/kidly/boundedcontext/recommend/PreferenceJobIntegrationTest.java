package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.app.preference.PreferenceJobResult;
import com.bbcc.kidly.boundedcontext.recommend.app.preference.PreferenceJobService;
import com.bbcc.kidly.boundedcontext.recommend.domain.activity.ActivityType;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
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

        LocalDateTime before = now();

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
        jdbc.update("""
            INSERT INTO recommend.activity_record (event_id, type, member_id, child_profile_id, occurred_at,
                product_id, category_id, created_at, updated_at)
            SELECT gen_random_uuid(), 'PRODUCT_VIEWED', 1, id, ?, 100, 10, now(), now()
            FROM generate_series(1, 150) AS id
            """, now().minusDays(1));
        jdbc.execute("""
            CREATE FUNCTION recommend.fail_child_120() RETURNS trigger AS $$
            BEGIN
                RAISE EXCEPTION 'child 120 fails';
            END;
            $$ LANGUAGE plpgsql
            """);
        try {
            jdbc.execute("""
                CREATE TRIGGER fail_child_120 BEFORE INSERT ON recommend.child_category_affinity
                FOR EACH ROW WHEN (NEW.child_profile_id = 120) EXECUTE FUNCTION recommend.fail_child_120()
                """);

            assertThat(service.run(today())).isEqualTo(PreferenceJobResult.FAILED);
            assertThat(categoryChildIds()).containsExactlyElementsOf(range(1, 100));
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS fail_child_120 ON recommend.child_category_affinity");
            jdbc.execute("DROP FUNCTION IF EXISTS recommend.fail_child_120()");
        }
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

    // 오늘 기준일을 한 번 실행한 뒤, 서버가 자녀 Step 도중 죽은 것처럼 Job · 자녀 Step 실행을 STARTED로 되돌린다.
    // Batch 저장소 API는 이 테스트 모듈에서 보이지 않아 실제 실행 기록을 SQL로 고친다
    private void leaveRunningExecution(LocalDateTime lastActivity) {
        assertThat(service.run(today())).isEqualTo(PreferenceJobResult.COMPLETED);
        jdbc.update("""
            UPDATE recommend.batch_job_execution
            SET status = 'STARTED', exit_code = 'UNKNOWN', end_time = NULL, start_time = ?, last_updated = ?
            """, lastActivity, lastActivity);
        jdbc.update("""
            UPDATE recommend.batch_step_execution
            SET status = 'STARTED', exit_code = 'EXECUTING', end_time = NULL, start_time = ?, last_updated = ?
            WHERE step_name = 'childPreferenceStep'
            """, lastActivity, lastActivity);
        jdbc.update("""
            UPDATE recommend.batch_step_execution SET last_updated = ? WHERE step_name <> 'childPreferenceStep'
            """, lastActivity);
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
