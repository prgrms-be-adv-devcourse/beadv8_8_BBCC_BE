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
 * 빈 DB에서 kidly-app이 먼저 뜨면 자녀 테이블만 있고 아웃박스는 없다(#682). 그 DB에서 서비스 Flyway가 아웃박스를 만드는지 본다.
 * 서비스가 뜨기 전의 DB를 만들어야 하므로 스프링 컨텍스트(@IntegrationTest) 없이 Flyway를 직접 돌린다.
 */
@Tag("integration")
class ChildProfileMigrationIntegrationTest {

    private static final String INIT_VERSION = "202610160256";

    @Test
    @DisplayName("자녀 테이블만 있는 DB에서 baseline을 잡아도 아웃박스를 만든다")
    void createsOutboxWhenOnlyChildTablesExist() {
        try (var postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:18"))) {
            postgres.start();
            var dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword());
            var jdbc = new JdbcTemplate(dataSource);
            createChildTablesLikeKidlyAppInit(dataSource, jdbc);

            serviceFlyway(dataSource).migrate();

            assertThat(childProfileTables(jdbc)).contains("child_profile", "growth_record", "child_profile_outbox");
        }
    }

    // kidly-app의 init이 만드는 자녀 테이블은 자녀 init과 같다. 자녀 init만 적용하고 그 이력은 지워 kidly-app이 만든 상태로 둔다
    private static void createChildTablesLikeKidlyAppInit(DriverManagerDataSource dataSource, JdbcTemplate jdbc) {
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .defaultSchema("childprofile")
            .schemas("childprofile")
            .table("kidly_app_init_history")
            .target(INIT_VERSION)
            .load()
            .migrate();
        jdbc.execute("DROP TABLE childprofile.kidly_app_init_history");
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

    private static List<String> childProfileTables(JdbcTemplate jdbc) {
        return jdbc.queryForList("SELECT tablename FROM pg_tables WHERE schemaname = 'childprofile'", String.class);
    }
}
