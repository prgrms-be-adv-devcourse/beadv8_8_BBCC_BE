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
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 서비스가 처음 뜰 때 상품 테이블이 이미 있는 DB에서 서비스 Flyway가 baseline을 잡고 나서의 스키마를 본다(#791).
 * kidly-app이 상품을 가지고 있던 동안 쓰던 DB(dev · 공유 로컬)에는 kidly-app이 상품 마이그레이션을 모두 적용해 두었다.
 * baseline-version은 서비스가 처음 뜰 때 한 번만 쓰이므로 고정해 두었고, 그 사이에 더한 상품 마이그레이션은 두 앱이 모두 적용했다.
 * 그래서 이 DB에 서비스 Flyway를 돌려도 스키마가 바뀌지 않아야 한다.
 * 상품 분리 당시(#836) kidly-app은 자기 폴더의 init · 기준 데이터 soft delete까지만 적용했다.
 * 서비스는 그 뒤 아웃박스 버전으로 baseline을 잡아 아웃박스 마이그레이션을 건너뛰므로, 그런 DB에서도 모든 상품 마이그레이션을 적용한 DB와 스키마가 같아야 한다.
 * 서비스가 뜨기 전의 DB를 만들어야 하므로 스프링 컨텍스트(@IntegrationTest) 없이 Flyway를 직접 돌린다.
 */
@Tag("integration")
class ProductMigrationIntegrationTest {

    private static final String BASELINE_VERSION = "202610292246";
    // kidly-app 폴더에 있던 마지막 상품 마이그레이션(기준 데이터 soft delete)
    private static final String LAST_KIDLY_APP_MODULE_VERSION = "202610181146";
    // 카테고리 보일 성별 마이그레이션 바로 앞의 상품 마이그레이션
    private static final String BEFORE_VISIBLE_GENDER_VERSION = "202611101703";
    private static final String VISIBLE_GENDER_MIGRATION =
        "db/migration/product/V202611111501__category_visible_gender.sql";

    @Test
    @DisplayName("kidly-app이 상품 마이그레이션을 모두 적용한 DB에서는 baseline을 잡고 스키마를 바꾸지 않는다")
    void keepsSchemaWhenKidlyAppAppliedAllMigrations() {
        try (var postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:18"))) {
            postgres.start();
            var dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword());
            var jdbc = new JdbcTemplate(dataSource);
            createProductTablesLikeKidlyApp(dataSource, jdbc, null);
            var before = schema(jdbc);

            serviceFlyway(dataSource).load().migrate();

            assertThat(schema(jdbc)).isEqualTo(before);
            assertThat(historyTypes(jdbc)).first().isEqualTo("BASELINE " + BASELINE_VERSION);
        }
    }

    @Test
    @DisplayName("kidly-app 폴더의 상품 파일까지만 있는 DB에서 baseline을 잡아도 모든 상품 마이그레이션을 적용한 DB와 스키마가 같다")
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
            createProductTablesLikeKidlyApp(fullDataSource, new JdbcTemplate(fullDataSource), null);
            createProductTablesLikeKidlyApp(dataSource, jdbc, LAST_KIDLY_APP_MODULE_VERSION);

            serviceFlyway(dataSource).load().migrate();

            assertThat(schema(jdbc)).isEqualTo(schema(new JdbcTemplate(fullDataSource)));
            assertThat(historyTypes(jdbc)).first().isEqualTo("BASELINE " + BASELINE_VERSION);
        }
    }

    @Test
    @DisplayName("VIS-01 카테고리가 있는 DB에서 원피스 · 치마를 GIRL로 두고, 하의를 바지로 바꾸고, 총장 · 허리단면을 필수 실측으로 가진 치마를 넣으며, "
        + "보일 성별 마이그레이션을 한 번 더 돌려도 행이 그대로다")
    void migratesCategoryVisibleGenderOnExistingCategories() {
        try (var postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:18"))) {
            postgres.start();
            var dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword());
            var jdbc = new JdbcTemplate(dataSource);
            createProductTablesLikeKidlyApp(dataSource, jdbc, BEFORE_VISIBLE_GENDER_VERSION);
            insertCategoriesLikeDev(jdbc);

            serviceFlyway(dataSource).load().migrate();
            var migrated = categoryRows(jdbc);
            new ResourceDatabasePopulator(new ClassPathResource(VISIBLE_GENDER_MIGRATION)).execute(dataSource);

            assertThat(migrated).containsExactly(
                "의류 - ALL -", "상의 의류 ALL -", "바지 의류 ALL TOTAL_LENGTH,WAIST_WIDTH",
                "아우터 의류 ALL -", "원피스 의류 GIRL -", "잡화 - ALL -", "모자 잡화 ALL -",
                "치마 의류 GIRL TOTAL_LENGTH,WAIST_WIDTH");
            assertThat(productTypeIdOf(jdbc, "치마")).isEqualTo(productTypeIdOf(jdbc, "바지"));
            assertThat(categoryRows(jdbc)).isEqualTo(migrated);
        }
    }

    @Test
    @DisplayName("VIS-01 관리자가 의류 아래에 이미 만든 치마가 있으면 새로 넣지 않고 그 치마를 GIRL로 둔다")
    void keepsExistingSkirtAndMarksItGirl() {
        try (var postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:18"))) {
            postgres.start();
            var dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword());
            var jdbc = new JdbcTemplate(dataSource);
            createProductTablesLikeKidlyApp(dataSource, jdbc, BEFORE_VISIBLE_GENDER_VERSION);
            insertCategoriesLikeDev(jdbc);
            jdbc.update("INSERT INTO product.category (name, parent_id, product_type_id, created_at, updated_at) "
                + "VALUES ('치마', 1, 1, now(), now())");

            serviceFlyway(dataSource).load().migrate();

            assertThat(categoryRows(jdbc)).filteredOn(row -> row.startsWith("치마 "))
                .containsExactly("치마 의류 GIRL TOTAL_LENGTH,WAIST_WIDTH");
        }
    }

    // kidly-app이 적용한 상품 마이그레이션은 이 서비스의 것과 같은 버전 · 내용이다.
    // target 버전까지(null이면 모두) 적용하고 그 이력은 지워 kidly-app이 만든 상태로 둔다
    private static void createProductTablesLikeKidlyApp(DriverManagerDataSource dataSource, JdbcTemplate jdbc,
        String target) {
        var flyway = Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .defaultSchema("product")
            .schemas("product")
            .table("kidly_app_init_history");
        if (target != null) {
            flyway.target(target);
        }
        flyway.load().migrate();
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

    // 상품 스키마의 칸(표 · 칸 · 타입 · null 허용), 제약 이름, 인덱스 정의. 서비스 이력 테이블은 뺀다
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
        var indexes = jdbc.queryForList("""
            SELECT indexdef FROM pg_indexes
            WHERE schemaname = 'product' AND tablename NOT LIKE 'flyway_schema_history%'
            ORDER BY 1
            """, String.class);
        return Stream.of(columns, constraints, indexes).flatMap(List::stream).toList();
    }

    // dev처럼 의류 아래 상의 · 하의 · 아우터 · 원피스, 잡화 아래 모자를 둔다. 하의만 총장 · 허리단면을 필수 실측으로 가진다
    private static void insertCategoriesLikeDev(JdbcTemplate jdbc) {
        jdbc.update("INSERT INTO product.product_type (id, name, created_at, updated_at) VALUES "
            + "(1, '의류', now(), now()), (2, '모자', now(), now())");
        jdbc.update("INSERT INTO product.measurement_item (id, name, code, unit, created_at, updated_at) VALUES "
            + "(1, '총장', 'TOTAL_LENGTH', 'cm', now(), now()), (2, '허리단면', 'WAIST_WIDTH', 'cm', now(), now())");
        jdbc.update("INSERT INTO product.category (id, name, parent_id, product_type_id, created_at, updated_at) "
            + "VALUES "
            + "(1, '의류', null, null, now(), now()), (2, '상의', 1, 1, now(), now()), (3, '하의', 1, 1, now(), now()), "
            + "(4, '아우터', 1, 1, now(), now()), (5, '원피스', 1, 1, now(), now()), (6, '잡화', null, null, now(), now()), "
            + "(7, '모자', 6, 2, now(), now())");
        jdbc.execute("SELECT setval('product.category_id_seq', 7)");
        jdbc.update("INSERT INTO product.category_measurement_item "
            + "(category_id, measurement_item_id, sort_order, created_at, updated_at) VALUES "
            + "(3, 1, 1, now(), now()), (3, 2, 2, now(), now())");
    }

    // 카테고리마다 "이름 부모 보일성별 필수실측"을 ID 순서로 담는다. 부모 · 실측이 없으면 "-"다
    private static List<String> categoryRows(JdbcTemplate jdbc) {
        return jdbc.queryForList("""
            SELECT c.name || ' ' || coalesce(p.name, '-') || ' ' || c.visible_gender || ' ' || coalesce((
                SELECT string_agg(m.code, ',' ORDER BY l.sort_order)
                FROM product.category_measurement_item l
                JOIN product.measurement_item m ON m.id = l.measurement_item_id
                WHERE l.category_id = c.id), '-')
            FROM product.category c LEFT JOIN product.category p ON p.id = c.parent_id
            ORDER BY c.id
            """, String.class);
    }

    private static Long productTypeIdOf(JdbcTemplate jdbc, String categoryName) {
        return jdbc.queryForObject("SELECT product_type_id FROM product.category WHERE name = ?", Long.class,
            categoryName);
    }

    private static List<String> historyTypes(JdbcTemplate jdbc) {
        return jdbc.queryForList("""
            SELECT type || ' ' || version FROM product.flyway_schema_history_product
            WHERE version IS NOT NULL ORDER BY installed_rank
            """, String.class);
    }

}
