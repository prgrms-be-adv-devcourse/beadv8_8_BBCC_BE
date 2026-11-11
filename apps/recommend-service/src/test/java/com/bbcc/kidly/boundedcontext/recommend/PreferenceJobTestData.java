package com.bbcc.kidly.boundedcontext.recommend;

import java.time.LocalDateTime;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 선호 Job 통합 테스트가 실행 기록을 되돌리거나 저장을 실패시킬 때 쓰는 SQL이다.
 * Batch 저장소 API는 이 테스트 모듈에서 보이지 않아 실제 실행 기록을 SQL로 고친다.
 */
final class PreferenceJobTestData {

    private PreferenceJobTestData() {
    }

    static void insertChildrenViewed(JdbcTemplate jdbc, int count, LocalDateTime occurredAt) {
        jdbc.update("""
            INSERT INTO recommend.activity_record (event_id, type, member_id, child_profile_id, occurred_at,
                product_id, category_id, created_at, updated_at)
            SELECT gen_random_uuid(), 'PRODUCT_VIEWED', 1, id, ?, 100, 10, now(), now()
            FROM generate_series(1, ?) AS id
            """, occurredAt, count);
    }

    /**
     * 서버가 자녀 Step 도중 죽은 것처럼 Job · 자녀 Step 실행을 STARTED로 두고 마지막 진행 시각을 바꾼다.
     */
    static void markExecutionsRunning(JdbcTemplate jdbc, LocalDateTime lastActivity) {
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

    static void failInserts(JdbcTemplate jdbc, String table, String condition) {
        jdbc.execute("""
            CREATE FUNCTION recommend.fail_insert() RETURNS trigger AS $$
            BEGIN
                RAISE EXCEPTION 'insert fails';
            END;
            $$ LANGUAGE plpgsql
            """);
        jdbc.execute("CREATE TRIGGER fail_insert BEFORE INSERT ON recommend." + table
            + " FOR EACH ROW WHEN (" + condition + ") EXECUTE FUNCTION recommend.fail_insert()");
    }

    static void stopFailingInserts(JdbcTemplate jdbc, String table) {
        jdbc.execute("DROP TRIGGER IF EXISTS fail_insert ON recommend." + table);
        jdbc.execute("DROP FUNCTION IF EXISTS recommend.fail_insert()");
    }
}
