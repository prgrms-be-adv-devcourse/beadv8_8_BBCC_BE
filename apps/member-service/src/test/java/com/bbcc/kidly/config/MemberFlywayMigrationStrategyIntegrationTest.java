package com.bbcc.kidly.config;

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

/**
 * kidly-app이 회원 · 인증 · 판매자 마이그레이션을 어디까지 적용했든 서비스 Flyway가 지금 스키마까지 맞추는지 본다.
 * kidly-app이 만든 DB는 이 서비스의 마이그레이션을 kidly-app 이력 자리(public.flyway_schema_history)에 적용해 흉내 낸다.
 * 서비스가 뜨기 전의 DB를 만들어야 하므로 스프링 컨텍스트(@IntegrationTest) 없이 Flyway를 직접 돌린다.
 */
@Tag("integration")
class MemberFlywayMigrationStrategyIntegrationTest {

    // 회원 도메인을 뺀 kidly-app에 남는 회원 파일 중 마지막 버전(Access Token 무효화 표)
    private static final String KIDLY_APP_LAST_MEMBER_VERSION = "202610201313";
    private static final String LAST_VERSION = "202611031234";
    // 인증 스키마를 만든 버전. 바로 뒤 회원 가입 키(202611021421)는 kidly-app 이력에 없는 경우를 본다
    private static final String AUTH_SCHEMA_VERSION = "202611021206";

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
    @DisplayName("회원 도메인을 뺀 kidly-app이 먼저 만든 DB에서는 kidly-app 파일까지 건너뛰고 도메인 마이그레이션을 적용한다")
    void migratesDomainFilesAfterKidlyAppFiles() {
        migrateLikeKidlyApp(KIDLY_APP_LAST_MEMBER_VERSION);

        new MemberFlywayMigrationStrategy().migrate(serviceFlyway());

        assertThat(baselineVersion()).isEqualTo(KIDLY_APP_LAST_MEMBER_VERSION);
        assertThat(tables("member")).contains("member_outbox").doesNotContain("access_token_revocation");
        assertThat(tables("auth")).contains("social_account", "refresh_token", "admin_account");
    }

    @Test
    @DisplayName("kidly-app이 도메인 마이그레이션까지 적용한 DB(dev · prod)에서는 모두 건너뛴다")
    void skipsAllWhenKidlyAppAppliedEverything() {
        migrateLikeKidlyApp(LAST_VERSION);

        new MemberFlywayMigrationStrategy().migrate(serviceFlyway());

        assertThat(baselineVersion()).isEqualTo(LAST_VERSION);
        assertThat(appliedServiceVersions()).isEmpty();
    }

    @Test
    @DisplayName("kidly-app 이력 중간에 빠진 버전이 있으면 그 앞에서 잡고 빠진 버전부터 적용한다")
    void stopsBeforeMissingVersion() {
        migrateLikeKidlyApp(AUTH_SCHEMA_VERSION);
        // 먼저 만든 PR의 마이그레이션(out-of-order)처럼 그 사이 버전은 없고 더 높은 버전만 이력에 있다
        jdbc.update("""
            INSERT INTO public.flyway_schema_history
                (installed_rank, version, description, type, script, installed_by, execution_time, success)
            SELECT max(installed_rank) + 1, ?, 'drop member role', 'SQL', 'later.sql', 'kidly', 0, true
            FROM public.flyway_schema_history
            """, LAST_VERSION);

        new MemberFlywayMigrationStrategy().migrate(serviceFlyway());

        assertThat(baselineVersion()).isEqualTo(AUTH_SCHEMA_VERSION);
        assertThat(jdbc.queryForObject("""
            SELECT count(*) FROM information_schema.columns
            WHERE table_schema = 'member' AND table_name = 'member' AND column_name = 'registration_key'
            """, Integer.class)).isOne();
    }

    // kidly-app 설정(이력 public.flyway_schema_history)으로 이 서비스의 마이그레이션을 target 버전까지 적용한다
    private void migrateLikeKidlyApp(String target) {
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .defaultSchema("public")
            .schemas("public", "member", "seller")
            .target(target)
            .load()
            .migrate();
    }

    // 서비스 application.yml의 spring.flyway 값 그대로
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

    private List<String> appliedServiceVersions() {
        return jdbc.queryForList(
            "SELECT version FROM member.flyway_schema_history_member WHERE type = 'SQL'", String.class);
    }

    private List<String> tables(String schema) {
        return jdbc.queryForList("SELECT tablename FROM pg_tables WHERE schemaname = ?", String.class, schema);
    }
}
