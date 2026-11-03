package com.bbcc.kidly;

import static org.assertj.core.api.Assertions.assertThatCode;

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
 * 회원 · 인증 · 판매자 마이그레이션을 회원 서비스로 옮긴 뒤에도, 옛 앱이 그 마이그레이션을 적용한 DB(dev · prod)에서 앱이 뜨는지 본다.
 * 옮긴 버전은 이 앱의 마지막 버전보다 낮으면 missing, 높으면 future로 남는다. 빈 DB로 도는 다른 테스트는 이 상태를 만들지 않는다.
 */
@Tag("integration")
class KidlyAppMigrationIntegrationTest {

    // 회원 서비스로 옮긴 회원 아웃박스 · 회원 역할 삭제 버전. 앞은 이 앱의 마지막 버전보다 낮고 뒤는 높다
    private static final String MOVED_LOWER_VERSION = "202610301129";
    private static final String MOVED_HIGHER_VERSION = "202611031234";

    @Test
    @DisplayName("회원 서비스로 옮긴 마이그레이션의 이력이 남아 있어도 검증을 통과한다")
    void ignoresMovedMemberMigrations() {
        try (var postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:18"))) {
            postgres.start();
            var dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword());
            appFlyway(dataSource).migrate();
            var jdbc = new JdbcTemplate(dataSource);
            insertHistory(jdbc, MOVED_LOWER_VERSION);
            insertHistory(jdbc, MOVED_HIGHER_VERSION);

            assertThatCode(() -> appFlyway(dataSource).migrate()).doesNotThrowAnyException();
        }
    }

    private static void insertHistory(JdbcTemplate jdbc, String version) {
        jdbc.update("""
            INSERT INTO public.flyway_schema_history
                (installed_rank, version, description, type, script, installed_by, execution_time, success)
            SELECT max(installed_rank) + 1, ?, 'moved', 'SQL', 'member/moved.sql', 'kidly', 0, true
            FROM public.flyway_schema_history
            """, version);
    }

    // 앱 application.yml의 spring.flyway 값 그대로
    private static Flyway appFlyway(DriverManagerDataSource dataSource) {
        var config = appConfig();
        return Flyway.configure()
            .dataSource(dataSource)
            .locations(config.getProperty("spring.flyway.locations"))
            .defaultSchema(config.getProperty("spring.flyway.default-schema"))
            .schemas(config.getProperty("spring.flyway.schemas").split(","))
            .baselineOnMigrate(Boolean.parseBoolean(config.getProperty("spring.flyway.baseline-on-migrate")))
            .baselineVersion(config.getProperty("spring.flyway.baseline-version"))
            .outOfOrder(Boolean.parseBoolean(config.getProperty("spring.flyway.out-of-order")))
            .ignoreMigrationPatterns(config.getProperty("spring.flyway.ignore-migration-patterns").split(","))
            .load();
    }

    private static Properties appConfig() {
        var yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        return yaml.getObject();
    }
}
