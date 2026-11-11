package com.bbcc.kidly;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 서비스가 처음 뜰 때 추천 테이블이 이미 있는 DB에서 서비스 Flyway가 baseline을 잡고 나서의 스키마를 본다(#704).
 * 예전에 kidly-app이 만든 DB에서는 추천 테이블은 init 모양으로만 있다. 추천 마이그레이션은 이제 이 서비스에만 있어 kidly-app이 적용하지 않는다.
 * kidly-app이 추천을 가지고 있던 동안 쓰던 DB에는 마지막 추천 마이그레이션(202611021557)까지 적용돼 있다.
 * 서비스가 뜨기 전의 DB를 만들어야 하므로 스프링 컨텍스트(@IntegrationTest) 없이 Flyway를 직접 돌린다.
 */
@Tag("integration")
class RecommendMigrationIntegrationTest {

    private static final String INIT_VERSION = "202610160256";
    private static final String LAST_KIDLY_APP_VERSION = "202611021557";
    private static final String PRODUCT_DETAILS_IF_MISSING_VERSION = "202611030905";
    private static final String BEFORE_PRODUCT_VIEW_UNIQUE_VERSION = "202611061217";
    private static final String BEFORE_GARMENT_PART_SPLIT_VERSION = "202611102156";

    @Test
    @DisplayName("추천 init 테이블만 있는 DB에서 baseline을 잡아도 상품 복사본의 브랜드 · 설명 칸과 사이즈별 실측 표를 만든다")
    void createsProductDetailsWhenOnlyInitTablesExist() {
        try (var postgres = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg18-trixie").asCompatibleSubstituteFor("postgres"))) {
            postgres.start();
            var dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword());
            var jdbc = new JdbcTemplate(dataSource);
            createRecommendTablesLikeKidlyApp(dataSource, jdbc, INIT_VERSION);

            serviceFlyway(dataSource).load().migrate();

            assertThat(columns(jdbc, "replica_product")).contains("brand", "description");
            assertThat(recommendTables(jdbc)).contains("replica_product", "replica_product_size_spec");
        }
    }

    @Test
    @DisplayName("kidly-app이 마지막 추천 마이그레이션까지 적용한 DB에서는 baseline을 잡고, 보강 마이그레이션은 스키마를 바꾸지 않는다")
    void keepsSchemaWhenKidlyAppAppliedLastMigration() {
        try (var postgres = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg18-trixie").asCompatibleSubstituteFor("postgres"))) {
            postgres.start();
            var dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword());
            var jdbc = new JdbcTemplate(dataSource);
            createRecommendTablesLikeKidlyApp(dataSource, jdbc, LAST_KIDLY_APP_VERSION);
            var before = schema(jdbc);

            // 보강 마이그레이션 뒤의 마이그레이션은 새 표를 만들므로 보강 마이그레이션까지만 적용한다
            serviceFlyway(dataSource).target(PRODUCT_DETAILS_IF_MISSING_VERSION).load().migrate();

            assertThat(schema(jdbc)).isEqualTo(before);
            assertThat(historyTypes(jdbc)).first().isEqualTo("BASELINE " + LAST_KIDLY_APP_VERSION);
        }
    }

    @Test
    @DisplayName("kidly-app이 마지막 추천 마이그레이션까지 적용한 DB에서 baseline을 잡아도 활동 기록 표를 만든다")
    void createsActivityRecordAfterBaseline() {
        try (var postgres = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg18-trixie").asCompatibleSubstituteFor("postgres"))) {
            postgres.start();
            var dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword());
            var jdbc = new JdbcTemplate(dataSource);
            createRecommendTablesLikeKidlyApp(dataSource, jdbc, LAST_KIDLY_APP_VERSION);

            serviceFlyway(dataSource).load().migrate();

            assertThat(recommendTables(jdbc)).contains("activity_record");
        }
    }

    @Test
    @DisplayName("PREF-15 같은 날 같은 상세 진입이 쌓인 DB에 마이그레이션하면 가장 먼저 쌓인 한 줄만 남기고 다른 줄은 그대로 둔다")
    void keepFirstProductViewOfSameDay() {
        try (var postgres = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg18-trixie").asCompatibleSubstituteFor("postgres"))) {
            postgres.start();
            var dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword());
            var jdbc = new JdbcTemplate(dataSource);
            serviceFlyway(dataSource).target(BEFORE_PRODUCT_VIEW_UNIQUE_VERSION).load().migrate();
            LocalDateTime tenAm = LocalDateTime.of(2026, 11, 10, 10, 0);
            insertActivity(jdbc, 1, "PRODUCT_VIEWED", 1L, null, 31L, tenAm);
            insertActivity(jdbc, 2, "PRODUCT_VIEWED", 1L, null, 31L, tenAm.withHour(23).withMinute(59));
            insertActivity(jdbc, 3, "PRODUCT_VIEWED", 1L, null, 31L, tenAm.plusDays(1));
            insertActivity(jdbc, 4, "PRODUCT_VIEWED", 1L, 11L, 31L, tenAm.minusHours(1));
            insertActivity(jdbc, 5, "PRODUCT_VIEWED", 1L, 11L, 31L, tenAm);
            insertActivity(jdbc, 6, "PRODUCT_VIEWED", 2L, null, 31L, tenAm);
            insertActivity(jdbc, 7, "PRODUCT_VIEWED", 1L, null, 32L, tenAm);
            insertActivity(jdbc, 8, "CART_ITEM_ADDED", 1L, null, 31L, tenAm);
            insertActivity(jdbc, 9, "CART_ITEM_ADDED", 1L, null, 31L, tenAm);

            serviceFlyway(dataSource).load().migrate();

            assertThat(jdbc.queryForList("SELECT id FROM recommend.activity_record ORDER BY id", Long.class))
                .containsExactly(1L, 3L, 4L, 6L, 7L, 8L, 9L);
            assertThat(jdbc.queryForList(
                "SELECT indexname FROM pg_indexes WHERE schemaname = 'recommend' AND tablename = 'activity_record'",
                String.class)).contains("idx_activity_record_product_viewed_per_day");
        }
    }

    @Test
    @DisplayName("FIT-01 상의 칸의 원피스 · 아우터와 바지 칸의 치마 기준 옷은 원피스 · 아우터 · 치마 칸으로 옮기고, "
        + "세부 종류와 실측은 그대로 둔다. 같은 종류 칸의 것과 카테고리 경로를 모르는 것은 옮기지 않는다")
    void moveReferenceGarmentsToTheirOwnParts() {
        try (var postgres = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg18-trixie").asCompatibleSubstituteFor("postgres"))) {
            postgres.start();
            var dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword());
            var jdbc = new JdbcTemplate(dataSource);
            serviceFlyway(dataSource).target(BEFORE_GARMENT_PART_SPLIT_VERSION).load().migrate();
            insertProductOfCategory(jdbc, 31L, 7L, "상의");
            insertProductOfCategory(jdbc, 32L, 8L, "아우터");
            insertProductOfCategory(jdbc, 33L, 9L, "원피스");
            insertProductOfCategory(jdbc, 34L, 70L, "하의");
            insertProductOfCategory(jdbc, 35L, 71L, "치마");
            insertGarment(jdbc, 1, 1L, "TOP", 9L,
                "{\"TOTAL_LENGTH\": 60.0, \"CHEST_WIDTH\": 31.0, \"SLEEVE_LENGTH\": 30.0}");
            insertGarment(jdbc, 2, 2L, "TOP", 8L, "{\"TOTAL_LENGTH\": 48.0, \"CHEST_WIDTH\": 38.0}");
            insertGarment(jdbc, 3, 3L, "TOP", 7L, "{\"TOTAL_LENGTH\": 42.0, \"CHEST_WIDTH\": 34.5}");
            insertGarment(jdbc, 4, 1L, "BOTTOM", 71L, "{\"TOTAL_LENGTH\": 32.0, \"WAIST_WIDTH\": 24.0}");
            insertGarment(jdbc, 5, 2L, "BOTTOM", 70L, "{\"TOTAL_LENGTH\": 55.5, \"WAIST_WIDTH\": 26.0}");
            insertGarment(jdbc, 6, 3L, "BOTTOM", 999L, "{\"TOTAL_LENGTH\": 55.5, \"WAIST_WIDTH\": 26.0}");

            serviceFlyway(dataSource).load().migrate();

            assertThat(jdbc.queryForList("""
                SELECT id || ' ' || part || ' ' || category_id || ' ' || measurements::text
                FROM recommend.reference_garment ORDER BY id
                """, String.class)).containsExactly(
                "1 DRESS 9 {\"CHEST_WIDTH\": 31.0, \"TOTAL_LENGTH\": 60.0, \"SLEEVE_LENGTH\": 30.0}",
                "2 OUTER 8 {\"CHEST_WIDTH\": 38.0, \"TOTAL_LENGTH\": 48.0}",
                "3 TOP 7 {\"CHEST_WIDTH\": 34.5, \"TOTAL_LENGTH\": 42.0}",
                "4 SKIRT 71 {\"WAIST_WIDTH\": 24.0, \"TOTAL_LENGTH\": 32.0}",
                "5 BOTTOM 70 {\"WAIST_WIDTH\": 26.0, \"TOTAL_LENGTH\": 55.5}",
                "6 BOTTOM 999 {\"WAIST_WIDTH\": 26.0, \"TOTAL_LENGTH\": 55.5}");
        }
    }

    // 상품 복사본 하나와 그 카테고리 경로(의류 > 말단)를 넣는다
    private static void insertProductOfCategory(JdbcTemplate jdbc, Long productId, Long categoryId,
        String categoryName) {
        LocalDateTime at = LocalDateTime.of(2026, 11, 10, 10, 0);
        jdbc.update("""
            INSERT INTO recommend.replica_product (product_id, seller_id, name, category_id, status, is_size_free,
                product_registered_at, event_occurred_at, created_at, updated_at)
            VALUES (?, 1, '상품', ?, 'ON_SALE', false, ?, ?, ?, ?)
            """, productId, categoryId, at, at, at, at);
        jdbc.update("""
            INSERT INTO recommend.replica_product_category_path (product_id, position, category_id, category_name)
            VALUES (?, 0, 1, '의류'), (?, 1, ?, ?)
            """, productId, productId, categoryId, categoryName);
    }

    private static void insertGarment(JdbcTemplate jdbc, long id, Long childProfileId, String part, Long categoryId,
        String measurements) {
        jdbc.update("""
            INSERT INTO recommend.reference_garment
                (id, child_profile_id, part, category_id, measurements, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?::jsonb, now(), now())
            """, id, childProfileId, part, categoryId, measurements);
    }

    private static void insertActivity(JdbcTemplate jdbc, long id, String type, Long memberId, Long childProfileId,
        Long productId, LocalDateTime occurredAt) {
        jdbc.update("""
            INSERT INTO recommend.activity_record
                (id, event_id, type, member_id, child_profile_id, product_id, occurred_at, created_at, updated_at)
            VALUES (?, gen_random_uuid(), ?, ?, ?, ?, ?, ?, ?)
            """, id, type, memberId, childProfileId, productId, occurredAt, occurredAt, occurredAt);
    }

    // kidly-app이 적용한 추천 마이그레이션은 이 서비스의 것과 같다. target 버전까지 적용하고 그 이력은 지워 kidly-app이 만든 상태로 둔다
    private static void createRecommendTablesLikeKidlyApp(DriverManagerDataSource dataSource, JdbcTemplate jdbc,
        String target) {
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .defaultSchema("recommend")
            .schemas("recommend")
            .table("kidly_app_init_history")
            .target(target)
            .load()
            .migrate();
        jdbc.execute("DROP TABLE recommend.kidly_app_init_history");
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

    private static List<String> recommendTables(JdbcTemplate jdbc) {
        return jdbc.queryForList("SELECT tablename FROM pg_tables WHERE schemaname = 'recommend'", String.class);
    }

    // 추천 스키마의 칸(표 · 칸 · 타입 · null 허용)과 제약 이름. 서비스 이력 테이블은 뺀다
    private static List<String> schema(JdbcTemplate jdbc) {
        var columns = jdbc.queryForList("""
            SELECT table_name || '.' || column_name || ' ' || data_type || ' ' || is_nullable
            FROM information_schema.columns
            WHERE table_schema = 'recommend' AND table_name NOT LIKE 'flyway_schema_history%'
            ORDER BY 1
            """, String.class);
        var constraints = jdbc.queryForList("""
            SELECT conname FROM pg_constraint
            WHERE connamespace = 'recommend'::regnamespace AND conname NOT LIKE 'flyway_schema_history%'
            ORDER BY 1
            """, String.class);
        return Stream.concat(columns.stream(), constraints.stream()).toList();
    }

    private static List<String> historyTypes(JdbcTemplate jdbc) {
        return jdbc.queryForList("""
            SELECT type || ' ' || version FROM recommend.flyway_schema_history_recommend
            WHERE version IS NOT NULL ORDER BY installed_rank
            """, String.class);
    }

    private static List<String> columns(JdbcTemplate jdbc, String table) {
        return jdbc.queryForList("""
            SELECT column_name FROM information_schema.columns
            WHERE table_schema = 'recommend' AND table_name = ?
            """, String.class, table);
    }
}
