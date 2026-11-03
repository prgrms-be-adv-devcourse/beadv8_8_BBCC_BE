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
 * 서비스가 처음 뜰 때 상품 테이블이 이미 있는 DB에서 서비스 Flyway가 baseline을 잡고 나서의 스키마를 본다(#791).
 * kidly-app이 상품을 가지고 있는 동안 쓰는 DB(dev · prod · 공유 로컬)에는 마지막 상품 마이그레이션(202610292246)까지 적용돼 있다.
 * 서비스가 뜨기 전의 DB를 만들어야 하므로 스프링 컨텍스트(@IntegrationTest) 없이 Flyway를 직접 돌린다.
 */
@Tag("integration")
class ProductMigrationIntegrationTest {

    private static final String LAST_KIDLY_APP_VERSION = "202610292246";

    @Test
    @DisplayName("kidly-app이 마지막 상품 마이그레이션까지 적용한 DB에서는 baseline을 잡고 스키마를 바꾸지 않는다")
    void keepsSchemaWhenKidlyAppAppliedLastMigration() {
        try (var postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:18"))) {
            postgres.start();
            var dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword());
            var jdbc = new JdbcTemplate(dataSource);
            createProductTablesLikeKidlyApp(dataSource, jdbc, LAST_KIDLY_APP_VERSION);
            var before = schema(jdbc);

            serviceFlyway(dataSource).load().migrate();

            assertThat(schema(jdbc)).isEqualTo(before);
            assertThat(historyTypes(jdbc)).first().isEqualTo("BASELINE " + LAST_KIDLY_APP_VERSION);
        }
    }

    // kidly-app이 적용한 상품 마이그레이션은 이 서비스의 것과 같다. target 버전까지 적용하고 그 이력은 지워 kidly-app이 만든 상태로 둔다
    private static void createProductTablesLikeKidlyApp(DriverManagerDataSource dataSource, JdbcTemplate jdbc,
        String target) {
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .defaultSchema("product")
            .schemas("product")
            .table("kidly_app_init_history")
            .target(target)
            .load()
            .migrate();
        jdbc.execute("DROP TABLE product.kidly_app_init_history");
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

    // 상품 스키마의 칸(표 · 칸 · 타입 · null 허용)과 제약 이름. 서비스 이력 테이블은 뺀다
    private static List<String> schema(JdbcTemplate jdbc) {
        var columns = jdbc.queryForList("""
            SELECT table_name || '.' || column_name || ' ' || data_type || ' ' || is_nullable
            FROM information_schema.columns
            WHERE table_schema = 'product' AND table_name NOT LIKE 'flyway_schema_history%'
            ORDER BY 1
            """, String.class);
        var constraints = jdbc.queryForList("""
            SELECT conname FROM pg_constraint
            WHERE connamespace = 'product'::regnamespace AND conname NOT LIKE 'flyway_schema_history%'
            ORDER BY 1
            """, String.class);
        return Stream.concat(columns.stream(), constraints.stream()).toList();
    }

    private static List<String> historyTypes(JdbcTemplate jdbc) {
        return jdbc.queryForList("""
            SELECT type || ' ' || version FROM product.flyway_schema_history_product
            WHERE version IS NOT NULL ORDER BY installed_rank
            """, String.class);
    }

}
