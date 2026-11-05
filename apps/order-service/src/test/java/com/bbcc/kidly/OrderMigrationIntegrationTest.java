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
 * 서비스가 처음 뜰 때 주문 테이블이 이미 있는 DB에서 서비스 Flyway가 baseline을 잡고 나서의 스키마를 본다.
 * kidly-app이 주문을 가지고 있던 동안 쓰던 DB에는 주문 마이그레이션을 모두 적용해 두었다.
 * baseline-version은 서비스가 처음 뜰 때 한 번만 쓰이므로 고정해 두고, 그 뒤에 더한 주문 마이그레이션은 서비스가 적용한다.
 * 그래서 이 DB에 서비스 Flyway를 돌려도 스키마가 바뀌지 않아야 한다.
 * 주문 분리 당시(#826) kidly-app은 자기 폴더의 주문 파일(init의 orders 구문 · 옛 주문 파일 5개)까지만 적용했다.
 * 서비스는 그 뒤 baseline을 잡아 주문 모듈 마이그레이션을 건너뛰므로, 그런 DB에서도 모든 주문 마이그레이션을 적용한 DB와 스키마가 같아야 한다
 * (보강 마이그레이션 V202611041730).
 * 서비스가 뜨기 전의 DB를 만들어야 하므로 스프링 컨텍스트(@IntegrationTest) 없이 Flyway를 직접 돌린다.
 */
@Tag("integration")
class OrderMigrationIntegrationTest {

    private static final String BASELINE_VERSION = "202611031100";
    // kidly-app 폴더에 있던 마지막 주문 마이그레이션(이벤트 처리 실패 보관)
    private static final String LAST_KIDLY_APP_MODULE_VERSION = "202610191003";

    @Test
    @DisplayName("kidly-app이 주문 마이그레이션을 모두 적용한 DB에서는 baseline을 잡고 스키마를 바꾸지 않는다")
    void keepsSchemaWhenKidlyAppAppliedAllMigrations() {
        try (var postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:18"))) {
            postgres.start();
            var dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword());
            var jdbc = new JdbcTemplate(dataSource);
            createOrderTablesLikeKidlyApp(dataSource, jdbc, null);
            var before = schema(jdbc);

            serviceFlyway(dataSource).load().migrate();

            assertThat(schema(jdbc)).isEqualTo(before);
            assertThat(historyTypes(jdbc)).first().isEqualTo("BASELINE " + BASELINE_VERSION);
        }
    }

    @Test
    @DisplayName("kidly-app 폴더의 주문 파일까지만 있는 DB에서 baseline을 잡아도 모든 주문 마이그레이션을 적용한 DB와 스키마가 같다")
    void matchesFullSchemaWhenOnlyKidlyAppModuleMigrationsExist() {
        try (var postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:18"))) {
            postgres.start();
            var dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword());
            var jdbc = new JdbcTemplate(dataSource);
            jdbc.execute("CREATE DATABASE full_migration");
            var fullDataSource = new DriverManagerDataSource(
                postgres.getJdbcUrl().replace("/" + postgres.getDatabaseName(), "/full_migration"),
                postgres.getUsername(), postgres.getPassword());
            createOrderTablesLikeKidlyApp(fullDataSource, new JdbcTemplate(fullDataSource), null);
            createOrderTablesLikeKidlyApp(dataSource, jdbc, LAST_KIDLY_APP_MODULE_VERSION);

            serviceFlyway(dataSource).load().migrate();

            assertThat(schema(jdbc)).isEqualTo(schema(new JdbcTemplate(fullDataSource)));
            assertThat(historyTypes(jdbc)).first().isEqualTo("BASELINE " + BASELINE_VERSION);
        }
    }

    // kidly-app이 적용한 주문 마이그레이션(init의 orders 구문 · 옛 주문 파일 · 도메인 모듈 파일)은 이 서비스의 것과 같다.
    // target 버전까지(null이면 모두) 적용하고 그 이력은 지워 kidly-app이 만든 상태로 둔다
    private static void createOrderTablesLikeKidlyApp(DriverManagerDataSource dataSource, JdbcTemplate jdbc,
        String target) {
        var flyway = Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .defaultSchema("orders")
            .schemas("orders")
            .table("kidly_app_init_history");
        if (target != null) {
            flyway.target(target);
        }
        flyway.load().migrate();
        jdbc.execute("DROP TABLE orders.kidly_app_init_history");
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

    // 주문 스키마의 칸(표 · 칸 · 타입 · null 허용), 제약 이름, 인덱스 정의. 서비스 이력 테이블은 뺀다
    private static List<String> schema(JdbcTemplate jdbc) {
        var columns = jdbc.queryForList("""
            SELECT table_name || '.' || column_name || ' ' || data_type || ' ' || is_nullable
            FROM information_schema.columns
            WHERE table_schema = 'orders' AND table_name NOT LIKE 'flyway_schema_history%'
            ORDER BY 1
            """, String.class);
        var constraints = jdbc.queryForList("""
            SELECT conname FROM pg_constraint
            WHERE connamespace = 'orders'::regnamespace AND conname NOT LIKE 'flyway_schema_history%'
            ORDER BY 1
            """, String.class);
        var indexes = jdbc.queryForList("""
            SELECT indexdef FROM pg_indexes
            WHERE schemaname = 'orders' AND tablename NOT LIKE 'flyway_schema_history%'
            ORDER BY 1
            """, String.class);
        return Stream.of(columns, constraints, indexes).flatMap(List::stream).toList();
    }

    private static List<String> historyTypes(JdbcTemplate jdbc) {
        return jdbc.queryForList("""
            SELECT type || ' ' || version FROM orders.flyway_schema_history_order
            WHERE version IS NOT NULL ORDER BY installed_rank
            """, String.class);
    }

}
