package com.bbcc.kidly;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.member.config.ChildProfileFlywayConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 예전에 kidly-app이 만든 DB에서는 자녀 테이블만 있고 아웃박스는 없다(#682). 그 DB에서 서비스 Flyway가 아웃박스를 만드는지 본다.
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

    @Test
    @DisplayName("빈 DB에 자녀 스키마를 만든다")
    void migratesEmptyDatabase() {
        try (var postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:18"))) {
            postgres.start();
            var dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword());
            serviceFlyway(dataSource).migrate();
            assertThat(childProfileTables(new JdbcTemplate(dataSource)))
                .contains("child_profile", "growth_record", "child_profile_outbox");
        }
    }

    @Test
    @DisplayName("옛 자녀 서비스 이력을 검증하고 이어서 적용한다")
    void validatesAndMigratesLegacyHistory(@TempDir Path legacy) throws IOException {
        var root = Path.of("childprofile/src/main/resources/db/childprofile-migration");
        Files.createDirectories(legacy.resolve("childprofile"));
        Files.copy(root.resolve("V202610160256__init.sql"), legacy.resolve("V202610160256__init.sql"));
        Files.copy(root.resolve("childprofile/V202610300953__child_profile_outbox.sql"),
            legacy.resolve("childprofile/V202610300953__child_profile_outbox.sql"));
        try (var postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:18"))) {
            postgres.start();
            var dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword());
            Flyway.configure().dataSource(dataSource).locations("filesystem:" + legacy)
                .defaultSchema("childprofile").schemas("childprofile").table("flyway_schema_history_childprofile")
                .baselineOnMigrate(true).baselineVersion("202610300953").outOfOrder(true).load().migrate();
            var current = serviceFlyway(dataSource);
            current.migrate();
            current.validate();
            assertThat(childProfileTables(new JdbcTemplate(dataSource))).contains("child_profile_outbox");
        }
    }

    // kidly-app의 init이 만드는 자녀 테이블은 자녀 init과 같다. 자녀 init만 적용하고 그 이력은 지워 kidly-app이 만든 상태로 둔다
    private static void createChildTablesLikeKidlyAppInit(DriverManagerDataSource dataSource, JdbcTemplate jdbc) {
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/childprofile-migration")
            .defaultSchema("childprofile")
            .schemas("childprofile")
            .table("kidly_app_init_history")
            .target(INIT_VERSION)
            .load()
            .migrate();
        jdbc.execute("DROP TABLE childprofile.kidly_app_init_history");
    }

    private static Flyway serviceFlyway(DriverManagerDataSource dataSource) {
        return ChildProfileFlywayConfig.childProfileFlyway(dataSource);
    }

    private static List<String> childProfileTables(JdbcTemplate jdbc) {
        return jdbc.queryForList("SELECT tablename FROM pg_tables WHERE schemaname = 'childprofile'", String.class);
    }
}
