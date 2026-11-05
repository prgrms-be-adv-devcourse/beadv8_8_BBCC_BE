package com.bbcc.kidly;

import static org.assertj.core.api.Assertions.assertThat;

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

    @Test
    @DisplayName("추천 init 테이블만 있는 DB에서 baseline을 잡아도 상품 복사본의 브랜드 · 설명 칸과 사이즈별 실측 표를 만든다")
    void createsProductDetailsWhenOnlyInitTablesExist() {
        try (var postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:18"))) {
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
        try (var postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:18"))) {
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
        try (var postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:18"))) {
            postgres.start();
            var dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword());
            var jdbc = new JdbcTemplate(dataSource);
            createRecommendTablesLikeKidlyApp(dataSource, jdbc, LAST_KIDLY_APP_VERSION);

            serviceFlyway(dataSource).load().migrate();

            assertThat(recommendTables(jdbc)).contains("activity_record");
        }
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
