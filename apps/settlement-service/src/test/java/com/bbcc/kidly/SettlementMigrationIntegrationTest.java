package com.bbcc.kidly;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Properties;
import org.flywaydb.core.Flyway;
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
 * 서비스 Flyway(application.yml의 spring.flyway)가 빈 DB에서는 정산 스키마를 만들고, 정산 테이블이 이미 있는 DB(dev · prod · 공유 로컬)에서는
 * baseline을 잡고 넘어가는지 본다(#729). 서비스가 뜨기 전의 DB를 만들어야 하므로 스프링 컨텍스트(@IntegrationTest) 없이 Flyway를 직접 돌린다.
 */
@Tag("integration")
class SettlementMigrationIntegrationTest {

    // 정산 도메인 jar의 마지막 파일(판매자 · 기간 종료일 유일 제약). application.yml의 baseline-version과 같아야 한다
    private static final String BASELINE_VERSION = "202611021248";

    @Test
    @DisplayName("빈 DB에는 init부터 적용해 정산 · Batch 테이블을 정산 스키마에만 만들고, 이력도 정산 스키마에 둔다")
    void createsOnlySettlementSchemaOnEmptyDb() {
        try (var postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:18"))) {
            postgres.start();
            var dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword());
            var jdbc = new JdbcTemplate(dataSource);

            serviceFlyway(dataSource).migrate();

            assertThat(tables(jdbc)).containsExactlyInAnyOrder(
                "settlement.settlement", "settlement.settlement_item", "settlement.settlement_skip",
                "settlement.settlement_outbox",
                "settlement.flyway_schema_history_settlement",
                "settlement.batch_job_instance", "settlement.batch_job_execution",
                "settlement.batch_job_execution_params", "settlement.batch_step_execution",
                "settlement.batch_step_execution_context", "settlement.batch_job_execution_context");
            // version이 없는 행은 Flyway가 스키마를 만든 기록이다
            assertThat(jdbc.queryForList("""
                SELECT version FROM settlement.flyway_schema_history_settlement
                WHERE success AND version IS NOT NULL ORDER BY installed_rank
                """, String.class)).containsExactly("202610160256", "202610160855", "202610161145", "202610172016",
                "202610201008", "202610301630", BASELINE_VERSION, "202611031547");
        }
    }

    @Test
    @DisplayName("정산 테이블이 이미 있는 DB는 baseline을 잡고 파일을 다시 적용하지 않는다")
    void baselinesWhenSettlementTablesExist() {
        try (var postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:18"))) {
            postgres.start();
            var dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword());
            var jdbc = new JdbcTemplate(dataSource);
            createSettlementTablesLikeKidlyApp(dataSource, jdbc);

            serviceFlyway(dataSource).migrate();

            assertThat(jdbc.queryForList("""
                SELECT version FROM settlement.flyway_schema_history_settlement
                WHERE success AND version IS NOT NULL ORDER BY installed_rank
                """, String.class)).containsExactly(BASELINE_VERSION, "202611031547");
        }
    }

    // kidly-app이 만든 정산 테이블은 이 서비스 · 도메인의 파일과 같다. 파일을 모두 적용하고 그 이력은 지워 kidly-app이 만든 상태로 둔다
    private static void createSettlementTablesLikeKidlyApp(DriverManagerDataSource dataSource, JdbcTemplate jdbc) {
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .defaultSchema("settlement")
            .schemas("settlement")
            .table("kidly_app_history")
            .load()
            .migrate();
        jdbc.execute("DROP TABLE settlement.kidly_app_history");
    }

    // 서비스 application.yml의 spring.flyway 값 그대로
    private static Flyway serviceFlyway(DriverManagerDataSource dataSource) {
        var config = serviceConfig();
        return Flyway.configure()
            .dataSource(dataSource)
            .locations(config.getProperty("spring.flyway.locations"))
            .defaultSchema(config.getProperty("spring.flyway.default-schema"))
            .schemas(config.getProperty("spring.flyway.schemas"))
            .table(config.getProperty("spring.flyway.table"))
            .baselineOnMigrate(Boolean.parseBoolean(config.getProperty("spring.flyway.baseline-on-migrate")))
            .baselineVersion(config.getProperty("spring.flyway.baseline-version"))
            .outOfOrder(Boolean.parseBoolean(config.getProperty("spring.flyway.out-of-order")))
            .load();
    }

    private static Properties serviceConfig() {
        var yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        return yaml.getObject();
    }

    // 시스템 스키마를 뺀 모든 테이블을 "스키마.테이블"로. 정산 밖의 스키마에 만들어졌는지도 함께 본다
    private static List<String> tables(JdbcTemplate jdbc) {
        return jdbc.queryForList("""
            SELECT schemaname || '.' || lower(tablename) FROM pg_tables
            WHERE schemaname NOT IN ('pg_catalog', 'information_schema')
            """, String.class);
    }
}
