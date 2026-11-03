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
 * 빈 DB에서 kidly-app이 먼저 뜨면 추천 테이블은 init 모양으로만 있다(#704). 그 뒤 추천 마이그레이션은 이제 이 서비스에만 있어
 * kidly-app이 적용하지 않는다. 그 DB에서 서비스 Flyway가 baseline을 잡고도 빠진 칸과 표를 만드는지 본다.
 * 서비스가 뜨기 전의 DB를 만들어야 하므로 스프링 컨텍스트(@IntegrationTest) 없이 Flyway를 직접 돌린다.
 */
@Tag("integration")
class RecommendMigrationIntegrationTest {

    private static final String INIT_VERSION = "202610160256";

    @Test
    @DisplayName("추천 init 테이블만 있는 DB에서 baseline을 잡아도 상품 복사본의 브랜드 · 설명 칸과 사이즈별 실측 표를 만든다")
    void createsProductDetailsWhenOnlyInitTablesExist() {
        try (var postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:18"))) {
            postgres.start();
            var dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword());
            var jdbc = new JdbcTemplate(dataSource);
            createRecommendTablesLikeKidlyAppInit(dataSource, jdbc);

            serviceFlyway(dataSource).migrate();

            assertThat(columns(jdbc, "replica_product")).contains("brand", "description");
            assertThat(recommendTables(jdbc)).contains("replica_product", "replica_product_size_spec");
        }
    }

    // kidly-app의 init이 만드는 추천 테이블은 추천 init과 같다. 추천 init만 적용하고 그 이력은 지워 kidly-app이 만든 상태로 둔다
    private static void createRecommendTablesLikeKidlyAppInit(DriverManagerDataSource dataSource, JdbcTemplate jdbc) {
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .defaultSchema("recommend")
            .schemas("recommend")
            .table("kidly_app_init_history")
            .target(INIT_VERSION)
            .load()
            .migrate();
        jdbc.execute("DROP TABLE recommend.kidly_app_init_history");
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

    private static List<String> recommendTables(JdbcTemplate jdbc) {
        return jdbc.queryForList("SELECT tablename FROM pg_tables WHERE schemaname = 'recommend'", String.class);
    }

    private static List<String> columns(JdbcTemplate jdbc, String table) {
        return jdbc.queryForList("""
            SELECT column_name FROM information_schema.columns
            WHERE table_schema = 'recommend' AND table_name = ?
            """, String.class, table);
    }
}
