package com.bbcc.kidly;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Properties;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Tag("integration")
class MemberMigrationIntegrationTest {

    private static final String KIDLY_APP_LAST_MEMBER_VERSION = "202610201313";

    private PostgreSQLContainer postgres;
    private DataSource dataSource;
    private JdbcTemplate jdbc;

    @BeforeEach
    void startDatabase() {
        postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:18"));
        postgres.start();
        dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        jdbc = new JdbcTemplate(dataSource);
    }

    @AfterEach
    void stopDatabase() {
        postgres.stop();
    }

    @Test
    @DisplayName("kidly-app이 먼저 만든 DB에서는 kidly-app 파일까지 건너뛰고 도메인 마이그레이션을 적용한다")
    void migratesDomainFilesAfterKidlyAppFiles() {
        migrateLikeKidlyApp();

        serviceFlyway().migrate();

        assertThat(baselineVersion()).isEqualTo(KIDLY_APP_LAST_MEMBER_VERSION);
        assertThat(tables("member")).contains("member_outbox").doesNotContain("access_token_revocation");
        assertThat(tables("auth")).contains("social_account", "refresh_token", "admin_account");
    }

    private void migrateLikeKidlyApp() {
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .defaultSchema("public")
            .schemas("public", "member", "seller")
            .target(KIDLY_APP_LAST_MEMBER_VERSION)
            .load()
            .migrate();
    }

    private Flyway serviceFlyway() {
        var config = serviceConfig();
        return Flyway.configure()
            .dataSource(dataSource)
            .locations(config.getProperty("spring.flyway.locations"))
            .defaultSchema(config.getProperty("spring.flyway.default-schema"))
            .schemas(config.getProperty("spring.flyway.schemas").split(","))
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

    private String baselineVersion() {
        return jdbc.queryForObject(
            "SELECT version FROM member.flyway_schema_history_member WHERE type = 'BASELINE'", String.class);
    }

    private List<String> tables(String schema) {
        return jdbc.queryForList("SELECT tablename FROM pg_tables WHERE schemaname = ?", String.class, schema);
    }
}
