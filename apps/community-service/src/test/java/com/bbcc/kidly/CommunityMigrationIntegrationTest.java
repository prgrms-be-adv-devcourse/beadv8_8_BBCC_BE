package com.bbcc.kidly;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 서비스가 처음 뜰 때 커뮤니티 테이블이 이미 있는 DB에서 서비스 Flyway가 baseline을 잡고 나서의 스키마를 본다.
 * kidly-app이 커뮤니티를 가지고 있던 동안 쓰던 DB에는 커뮤니티 마이그레이션을 모두 적용해 두었다.
 * baseline-version은 서비스가 처음 뜰 때 한 번만 쓰이므로 고정해 두고, 그 뒤에 더한 커뮤니티 마이그레이션은 서비스가 적용한다.
 * 그래서 이 DB에 서비스 Flyway를 돌려도 스키마가 바뀌지 않아야 한다.
 * 서비스가 뜨기 전의 DB를 만들어야 하므로 스프링 컨텍스트(@IntegrationTest) 없이 Flyway를 직접 돌린다.
 */
@Tag("integration")
class CommunityMigrationIntegrationTest {

    private static final String BASELINE_VERSION = "202611031200";

    @Test
    @DisplayName("kidly-app이 커뮤니티 마이그레이션을 모두 적용한 DB에서는 baseline을 잡고 스키마를 바꾸지 않는다")
    void keepsSchemaWhenKidlyAppAppliedAllMigrations() {
        try (var postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:18"))) {
            postgres.start();
            var dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword());
            var jdbc = new JdbcTemplate(dataSource);
            createCommunityTablesLikeKidlyApp(dataSource, jdbc);
            var before = schema(jdbc);

            serviceFlyway(dataSource).load().migrate();

            assertThat(schema(jdbc)).isEqualTo(before);
            assertThat(historyTypes(jdbc)).first().isEqualTo("BASELINE " + BASELINE_VERSION);
        }
    }

    @Test
    @DisplayName("baseline 버전까지만 적용된 DB에서는 baseline을 잡은 뒤 그보다 뒤 버전(리뷰 작성자 탈퇴 시각 칸)을 적용한다")
    void appliesMigrationsAfterBaseline() {
        try (var postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:18"))) {
            postgres.start();
            var dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword());
            var jdbc = new JdbcTemplate(dataSource);
            createCommunityTablesUpTo(dataSource, jdbc, BASELINE_VERSION);
            assertThat(reviewColumns(jdbc)).doesNotContain("author_withdrawn_at");

            serviceFlyway(dataSource).load().migrate();

            assertThat(reviewColumns(jdbc)).contains("author_withdrawn_at");
            assertThat(historyTypes(jdbc)).first().isEqualTo("BASELINE " + BASELINE_VERSION);
            assertThat(historyTypes(jdbc)).anySatisfy(type -> assertThat(type)
                .startsWith("SQL ").satisfies(sql -> assertThat(sql.substring(4)).isGreaterThan(BASELINE_VERSION)));
        }
    }

    @Test
    @DisplayName("baseline 버전까지만 적용된 DB에 리뷰 핏 칸을 더하고, 이미 있던 집계 행의 핏별 수는 0이다")
    void addsFitColumnsAfterBaseline() {
        try (var postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:18"))) {
            postgres.start();
            var dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword());
            var jdbc = new JdbcTemplate(dataSource);
            createCommunityTablesUpTo(dataSource, jdbc, BASELINE_VERSION);
            jdbc.update("insert into community.review_stats (product_id, review_count, rating_sum, created_at, "
                + "updated_at) values (14, 2, 9, now(), now())");

            serviceFlyway(dataSource).load().migrate();

            assertThat(reviewColumns(jdbc)).contains("fit");
            assertThat(jdbc.queryForMap("select fit_small, fit_true_to_size, fit_large from community.review_stats "
                + "where product_id = 14")).containsExactlyInAnyOrderEntriesOf(
                    Map.of("fit_small", 0L, "fit_true_to_size", 0L, "fit_large", 0L));
        }
    }

    @Test
    @DisplayName("baseline 버전까지만 적용된 DB에 리뷰 사진 표를 만들고, 리뷰를 지우면 그 사진 행도 지워지며 같은 리뷰의 같은 순서는 하나다")
    void addsReviewImageTableAfterBaseline() {
        try (var postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:18"))) {
            postgres.start();
            var dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword());
            var jdbc = new JdbcTemplate(dataSource);
            createCommunityTablesUpTo(dataSource, jdbc, BASELINE_VERSION);
            jdbc.update("insert into community.review (id, order_item_id, product_id, member_id, nickname, rating, "
                + "content, created_at, updated_at) values (1, 11, 14, 13, '닉', 4, '본문', now(), now())");

            serviceFlyway(dataSource).load().migrate();

            jdbc.update("insert into community.review_image (review_id, position, image_url, created_at) "
                + "values (1, 0, 'https://img/a.jpg', now())");
            assertThatThrownBy(() -> jdbc.update("insert into community.review_image (review_id, position, "
                + "image_url, created_at) values (1, 0, 'https://img/b.jpg', now())"))
                .isInstanceOf(DataIntegrityViolationException.class);
            jdbc.update("delete from community.review where id = 1");
            assertThat(jdbc.queryForObject("select count(*) from community.review_image", Long.class)).isZero();
        }
    }

    @Test
    @DisplayName("baseline 버전까지만 적용된 DB에 리뷰 검사 칸을 더하고, 이미 있던 리뷰는 검사 통과(PASSED) · 시도 0 · 버전 0이다")
    void addsModerationColumnsAfterBaseline() {
        try (var postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:18"))) {
            postgres.start();
            var dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword());
            var jdbc = new JdbcTemplate(dataSource);
            createCommunityTablesUpTo(dataSource, jdbc, BASELINE_VERSION);
            jdbc.update("insert into community.review (id, order_item_id, product_id, member_id, nickname, rating, "
                + "content, created_at, updated_at) values (1, 11, 14, 13, '닉', 4, '본문', now(), now())");

            serviceFlyway(dataSource).load().migrate();

            assertThat(jdbc.queryForMap("select moderation_status, moderation_reason, moderation_attempts, "
                + "content_version, moderated_at, moderated_by_admin_id from community.review where id = 1"))
                .containsEntry("moderation_status", "PASSED").containsEntry("moderation_attempts", 0)
                .containsEntry("content_version", 0L).containsEntry("moderation_reason", null)
                .containsEntry("moderated_at", null).containsEntry("moderated_by_admin_id", null);
            assertThatThrownBy(() -> jdbc.update("update community.review set moderation_status = 'UNKNOWN'"))
                .isInstanceOf(DataIntegrityViolationException.class);
            assertThat(jdbc.queryForList("select indexname from pg_indexes where schemaname = 'community' "
                + "and tablename = 'review'", String.class)).contains("idx_review_moderation_status");
        }
    }

    @Test
    @DisplayName("baseline 버전까지만 적용된 DB에 리뷰 사진 정리 대기 표를 만들고, 같은 key는 한 행 · 까닭은 두 값만이며 처리 순서 · 사진 주소 인덱스가 있다")
    void addsReviewImageCleanupTableAfterBaseline() {
        try (var postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:18"))) {
            postgres.start();
            var dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword());
            var jdbc = new JdbcTemplate(dataSource);
            createCommunityTablesUpTo(dataSource, jdbc, BASELINE_VERSION);

            serviceFlyway(dataSource).load().migrate();

            String insert = "insert into community.review_image_cleanup (object_key, reason, next_attempt_at, "
                + "created_at) values (?, ?, now(), now())";
            jdbc.update(insert, "dev/reviews/13/a.jpg", "REPLACED");
            assertThat(jdbc.queryForObject("select attempts from community.review_image_cleanup", Integer.class))
                .isZero();
            assertThatThrownBy(() -> jdbc.update(insert, "dev/reviews/13/a.jpg", "REVIEW_DELETED"))
                .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update(insert, "dev/reviews/13/b.jpg", "UNKNOWN"))
                .isInstanceOf(DataIntegrityViolationException.class);
            assertThat(jdbc.update("insert into community.review_image_cleanup (object_key, reason, next_attempt_at, "
                + "created_at) values ('dev/reviews/13/a.jpg', 'REPLACED', now(), now()) "
                + "on conflict (object_key) do nothing")).isZero();
            assertThat(jdbc.queryForList("select indexname from pg_indexes where schemaname = 'community'",
                String.class)).contains("idx_review_image_cleanup_next_attempt", "idx_review_image_image_url");
        }
    }

    @Test
    @DisplayName("baseline DB에 모두 적용하면 검사 상태 칸에 기본값이 없고, 이미 있던 리뷰의 값 · NOT NULL · CHECK는 그대로다")
    void dropsModerationStatusDefault() {
        try (var postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:18"))) {
            postgres.start();
            var dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword());
            var jdbc = new JdbcTemplate(dataSource);
            createCommunityTablesUpTo(dataSource, jdbc, BASELINE_VERSION);
            jdbc.update("insert into community.review (id, order_item_id, product_id, member_id, nickname, rating, "
                + "content, created_at, updated_at) values (1, 11, 14, 13, '닉', 4, '본문', now(), now())");

            serviceFlyway(dataSource).load().migrate();

            assertThat(jdbc.queryForMap("select column_default, is_nullable from information_schema.columns "
                + "where table_schema = 'community' and table_name = 'review' and column_name = 'moderation_status'"))
                .containsEntry("column_default", null).containsEntry("is_nullable", "NO");
            assertThat(jdbc.queryForObject("select moderation_status from community.review where id = 1",
                String.class)).isEqualTo("PASSED");
            assertThatThrownBy(() -> jdbc.update("update community.review set moderation_status = 'UNKNOWN'"))
                .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Test
    @DisplayName("활동 표를 만들며 지금 남은 작성 자격 · 리뷰 · 찜 · 검사 결정(통과 · 대기 제외)을 원본 시각으로 백필하고, "
        + "같은 백필을 다시 돌려도 행이 늘지 않는다")
    void backfillsActivityOnce() throws Exception {
        try (var postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:18"))) {
            postgres.start();
            var dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword());
            var jdbc = new JdbcTemplate(dataSource);
            createCommunityTablesUpTo(dataSource, jdbc, BASELINE_VERSION);
            serviceFlyway(dataSource).target("202611081000").load().migrate();
            jdbc.update("insert into community.review_eligibility (order_item_id, order_id, member_id, product_id, "
                + "option_id, size, confirmed_at, created_at, updated_at) values "
                + "(11, 1, 13, 14, 15, null, now(), '2026-11-01 10:00:00', now()), "
                + "(12, 1, 13, 14, 15, null, now(), '2026-11-01 10:00:00', now())");
            jdbc.update("insert into community.review (id, order_item_id, product_id, member_id, nickname, rating, "
                + "content, fit, created_at, updated_at, moderation_status, moderation_reason, moderated_at, "
                + "moderated_by_admin_id) values "
                + "(1, 11, 14, 13, '닉', 4, '본문', 'LARGE', '2026-11-02 10:00:00', now(), 'PASSED', null, now(), null), "
                + "(2, 12, 14, 13, '닉', 2, '본문', null, '2026-11-03 10:00:00', now(), 'HIDDEN', 'HATE', "
                + "'2026-11-04 10:00:00', null), "
                + "(3, 13, 14, 13, '닉', 3, '본문', null, '2026-11-03 10:00:00', now(), 'CONFIRMED_HIDDEN', 'HATE', "
                + "'2026-11-05 10:00:00', 99), "
                + "(4, 14, 14, 13, '닉', 5, '본문', null, '2026-11-03 10:00:00', now(), 'PENDING', null, null, null), "
                // 관리자가 다시 공개한 뒤 고쳐져 검사기가 숨긴 리뷰: 관리자 ID가 남아 있어도 HIDDEN은 검사기 결정이다
                + "(5, 15, 14, 13, '닉', 1, '본문', null, '2026-11-03 10:00:00', now(), 'HIDDEN', 'HATE', "
                + "'2026-11-07 10:00:00', 99)");
            jdbc.update("insert into community.review_image (review_id, position, image_url, created_at) values "
                + "(1, 0, 'https://img/a.jpg', now()), (1, 1, 'https://img/b.jpg', now())");
            jdbc.update("insert into community.favorite (member_id, product_id, created_at, updated_at) values "
                + "(13, 14, '2026-11-06 10:00:00', now()), (13, 15, '2026-11-06 11:00:00', now())");

            serviceFlyway(dataSource).load().migrate();

            Map<String, Long> counts = activityCounts(jdbc);
            assertThat(counts).containsExactlyInAnyOrderEntriesOf(Map.of("ELIGIBILITY_CREATED", 2L,
                "REVIEW_CREATED", 5L, "FAVORITE_ADDED", 2L, "MODERATION_DECIDED", 3L));
            assertThat(jdbc.queryForObject("select count(*) from community.activity "
                + "where attributes->>'backfilled' = 'true'", Long.class)).isEqualTo(12L);
            assertThat(jdbc.queryForMap("select occurred_at::text as at, attributes::text as attrs "
                + "from community.activity where type = 'REVIEW_CREATED' and aggregate_id = 1"))
                .containsEntry("at", "2026-11-02 10:00:00")
                .hasEntrySatisfying("attrs", attrs -> assertThat((String) attrs)
                    .contains("\"rating\": 4", "\"fit\": \"LARGE\"", "\"image_count\": 2",
                        "\"order_item_id\": 11"));
            assertThat(jdbc.queryForList("select attributes->>'by' || ':' || (attributes->>'status') || ':' "
                + "|| occurred_at::text from community.activity where type = 'MODERATION_DECIDED' "
                + "order by aggregate_id",
                String.class)).containsExactly("moderator:HIDDEN:2026-11-04 10:00:00",
                "admin:CONFIRMED_HIDDEN:2026-11-05 10:00:00", "moderator:HIDDEN:2026-11-07 10:00:00");
            assertThat(jdbc.queryForList("select indexname from pg_indexes where schemaname = 'community' "
                + "and tablename = 'activity'", String.class)).contains("idx_activity_occurred_at");

            // 같은 백필 문장을 다시 돌려도 행이 늘지 않는다
            for (String statement : backfillStatements()) {
                jdbc.update(statement);
            }
            assertThat(activityCounts(jdbc)).isEqualTo(counts);
        }
    }

    private static Map<String, Long> activityCounts(JdbcTemplate jdbc) {
        Map<String, Long> counts = new java.util.HashMap<>();
        jdbc.query("select type, count(*) from community.activity group by type",
            (org.springframework.jdbc.core.RowCallbackHandler) row -> counts.put(row.getString(1), row.getLong(2)));
        return counts;
    }

    // 활동 마이그레이션의 백필(INSERT) 문장들
    private static List<String> backfillStatements() throws java.io.IOException {
        String sql = new ClassPathResource("db/migration/community/V202611101700__community_activity.sql")
            .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return Stream.of(sql.split(";"))
            .map(statement -> statement.lines().filter(line -> !line.strip().startsWith("--"))
                .reduce("", (a, b) -> a + "\n" + b).strip())
            .filter(statement -> statement.startsWith("INSERT"))
            .toList();
    }

    // baseline 버전까지만 적용하고 이력은 지워 서비스가 처음 뜨기 전의 운영 DB 상태로 둔다
    private static void createCommunityTablesUpTo(DriverManagerDataSource dataSource, JdbcTemplate jdbc,
        String version) {
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .defaultSchema("community")
            .schemas("community")
            .table("kidly_app_init_history")
            .target(version)
            .load()
            .migrate();
        jdbc.execute("DROP TABLE community.kidly_app_init_history");
    }

    private static List<String> reviewColumns(JdbcTemplate jdbc) {
        return jdbc.queryForList("""
            SELECT column_name FROM information_schema.columns
            WHERE table_schema = 'community' AND table_name = 'review'
            """, String.class);
    }

    // kidly-app이 적용한 커뮤니티 마이그레이션(도메인 모듈 파일)은 이 서비스의 것과 같다.
    // 모두 적용하고 그 이력은 지워 kidly-app이 만든 상태로 둔다
    private static void createCommunityTablesLikeKidlyApp(DriverManagerDataSource dataSource, JdbcTemplate jdbc) {
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .defaultSchema("community")
            .schemas("community")
            .table("kidly_app_init_history")
            .load()
            .migrate();
        jdbc.execute("DROP TABLE community.kidly_app_init_history");
    }

    // 서비스 application.yml의 spring.flyway 값 그대로
    private static FluentConfiguration serviceFlyway(DriverManagerDataSource dataSource) {
        var config = serviceConfig();
        return Flyway.configure()
            .dataSource(dataSource)
            .locations(config.getProperty("spring.flyway.locations"))
            .defaultSchema(config.getProperty("spring.flyway.default-schema"))
            .schemas(config.getProperty("spring.flyway.schemas"))
            .table(config.getProperty("spring.flyway.table"))
            .baselineOnMigrate(Boolean.parseBoolean(config.getProperty("spring.flyway.baseline-on-migrate")))
            .baselineVersion(config.getProperty("spring.flyway.baseline-version"))
            .outOfOrder(Boolean.parseBoolean(config.getProperty("spring.flyway.out-of-order")));
    }

    private static Properties serviceConfig() {
        var yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        return yaml.getObject();
    }

    // 커뮤니티 스키마의 칸(표 · 칸 · 타입 · null 허용), 제약 이름, 인덱스 정의. 서비스 이력 테이블은 뺀다
    private static List<String> schema(JdbcTemplate jdbc) {
        var columns = jdbc.queryForList("""
            SELECT table_name || '.' || column_name || ' ' || data_type || ' ' || is_nullable
            FROM information_schema.columns
            WHERE table_schema = 'community' AND table_name NOT LIKE 'flyway_schema_history%'
            ORDER BY 1
            """, String.class);
        var constraints = jdbc.queryForList("""
            SELECT conname FROM pg_constraint
            WHERE connamespace = 'community'::regnamespace AND conname NOT LIKE 'flyway_schema_history%'
            ORDER BY 1
            """, String.class);
        var indexes = jdbc.queryForList("""
            SELECT indexdef FROM pg_indexes
            WHERE schemaname = 'community' AND tablename NOT LIKE 'flyway_schema_history%'
            ORDER BY 1
            """, String.class);
        return Stream.of(columns, constraints, indexes).flatMap(List::stream).toList();
    }

    private static List<String> historyTypes(JdbcTemplate jdbc) {
        return jdbc.queryForList("""
            SELECT type || ' ' || version FROM community.flyway_schema_history_community
            WHERE version IS NOT NULL ORDER BY installed_rank
            """, String.class);
    }

}
