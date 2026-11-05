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
 * 서비스 Flyway가 DB 상태에 따라 알림 테이블을 만들거나 건너뛰는지 본다.
 * kidly-app이 알림을 가지고 있는 동안 쓰던 DB(dev · prod · 공유 로컬)에는 kidly-app이 baseline 버전까지의 알림 테이블을 이미 만들어 두었다.
 * 그런 DB에서는 baseline을 잡고 그 뒤에 더한 알림 마이그레이션(도메인 모듈의 db/migration/notification)만 적용해,
 * 빈 DB에서 처음부터 적용한 것과 스키마가 같아야 한다.
 * 서비스가 뜨기 전의 DB를 만들어야 하므로 스프링 컨텍스트(@IntegrationTest) 없이 Flyway를 직접 돌린다.
 */
@Tag("integration")
class NotificationMigrationIntegrationTest {

    private static final String BASELINE_VERSION = "202610190901";

    @Test
    @DisplayName("kidly-app이 알림 테이블을 만든 DB에서는 baseline을 잡고, 그 뒤 마이그레이션만 적용해 빈 DB와 스키마가 같다")
    void baselinesWhenKidlyAppCreatedTables() {
        try (var postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:18"))) {
            postgres.start();
            var dataSource = dataSource(postgres);
            var jdbc = new JdbcTemplate(dataSource);
            var emptyDataSource = otherDatabase(postgres, jdbc, "empty_db");
            serviceFlyway(emptyDataSource).load().migrate();
            createNotificationTablesLikeKidlyApp(dataSource, jdbc);

            serviceFlyway(dataSource).load().migrate();

            assertThat(schema(jdbc)).isEqualTo(schema(new JdbcTemplate(emptyDataSource)));
            assertThat(historyTypes(jdbc)).first().isEqualTo("BASELINE " + BASELINE_VERSION);
            assertThat(historyTypes(jdbc)).doesNotContain("SQL " + BASELINE_VERSION);
        }
    }

    @Test
    @DisplayName("빈 DB에서는 이 서비스가 알림 테이블을 만들고, kidly-app이 만든 것과 스키마가 같다")
    void createsSameSchemaOnEmptyDatabase() {
        try (var postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:18"))) {
            postgres.start();
            var dataSource = dataSource(postgres);
            var jdbc = new JdbcTemplate(dataSource);
            var kidlyAppDataSource = otherDatabase(postgres, jdbc, "kidly_app");
            createNotificationTablesLikeKidlyApp(kidlyAppDataSource, new JdbcTemplate(kidlyAppDataSource));

            serviceFlyway(dataSource).load().migrate();

            // kidly-app이 만든 알림 테이블은 빈 DB에도 같은 모양으로 생긴다
            assertThat(schema(jdbc)).containsAll(schema(new JdbcTemplate(kidlyAppDataSource)));
            assertThat(historyTypes(jdbc)).first().isEqualTo("SQL " + BASELINE_VERSION);
        }
    }

    private static DriverManagerDataSource dataSource(PostgreSQLContainer postgres) {
        return new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    private static DriverManagerDataSource otherDatabase(PostgreSQLContainer postgres, JdbcTemplate jdbc, String name) {
        jdbc.execute("CREATE DATABASE " + name);
        return new DriverManagerDataSource(postgres.getJdbcUrl().replace("/" + postgres.getDatabaseName(), "/" + name),
            postgres.getUsername(), postgres.getPassword());
    }

    // kidly-app이 적용한 알림 마이그레이션은 이 서비스의 baseline 버전 파일과 같은 버전 · 구문이다.
    // baseline 버전까지만 적용하고 그 이력은 지워 kidly-app이 만든 상태로 둔다
    private static void createNotificationTablesLikeKidlyApp(DriverManagerDataSource dataSource, JdbcTemplate jdbc) {
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .defaultSchema("notification")
            .schemas("notification")
            .table("kidly_app_history")
            .target(BASELINE_VERSION)
            .load()
            .migrate();
        jdbc.execute("DROP TABLE notification.kidly_app_history");
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

    // 알림 스키마의 칸(표 · 칸 · 타입 · null 허용), 제약 이름, 인덱스 정의. 이력 테이블은 뺀다
    private static List<String> schema(JdbcTemplate jdbc) {
        var columns = jdbc.queryForList("""
            SELECT table_name || '.' || column_name || ' ' || data_type || ' ' || is_nullable
            FROM information_schema.columns
            WHERE table_schema = 'notification' AND table_name NOT LIKE 'flyway_schema_history%'
            ORDER BY 1
            """, String.class);
        var constraints = jdbc.queryForList("""
            SELECT conname FROM pg_constraint
            WHERE connamespace = 'notification'::regnamespace AND conname NOT LIKE 'flyway_schema_history%'
            ORDER BY 1
            """, String.class);
        var indexes = jdbc.queryForList("""
            SELECT indexdef FROM pg_indexes
            WHERE schemaname = 'notification' AND tablename NOT LIKE 'flyway_schema_history%'
            ORDER BY 1
            """, String.class);
        return Stream.of(columns, constraints, indexes).flatMap(List::stream).toList();
    }

    private static List<String> historyTypes(JdbcTemplate jdbc) {
        return jdbc.queryForList("""
            SELECT type || ' ' || version FROM notification.flyway_schema_history_notification
            WHERE version IS NOT NULL ORDER BY installed_rank
            """, String.class);
    }
}
