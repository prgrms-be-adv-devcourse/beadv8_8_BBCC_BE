package com.bbcc.kidly;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Properties;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.exception.FlywayValidateException;
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
 * 회원 · 인증 · 상품 마이그레이션을 각 서비스로 옮긴 뒤에도, 옛 앱이 그 마이그레이션을 적용한 DB(dev)에서 앱이 뜨는지 본다.
 * 빈 DB로 도는 다른 테스트는 옮긴 마이그레이션의 이력 행을 만들지 않는다.
 */
@Tag("integration")
class KidlyAppMigrationIntegrationTest {

    // 회원 서비스로 옮긴 회원 아웃박스 · 관리자 계정 비활성 파일. 앞은 이 앱의 마지막 버전보다 낮고 뒤는 높다
    private static final String MOVED_MEMBER_SCRIPT = "member/V202610301129__member_outbox.sql";
    private static final String MOVED_AUTH_SCRIPT = "auth/V202611031204__admin_account_disable.sql";
    // 상품 서비스로 옮긴 상품 모듈 파일 3개. 아웃박스는 이 앱의 마지막 버전보다 낮고 나머지 둘은 높다
    private static final String MOVED_PRODUCT_OUTBOX_SCRIPT = "product/V202610292246__product_outbox.sql";
    private static final String MOVED_PRODUCT_DROP_SEARCH_SCRIPT =
        "product/V202611040911__drop_product_search_document.sql";
    private static final String MOVED_PRODUCT_OUTBOX_IF_MISSING_SCRIPT =
        "product/V202611041223__product_outbox_if_missing.sql";

    // 주문 서비스 · 커뮤니티 서비스로 옮긴 도메인 모듈 파일(#826 · #827)
    private static final List<String> MOVED_ORDER_COMMUNITY_SCRIPTS = List.of(
        "orders/V202610292033__order_outbox.sql",
        "orders/V202610310156__order_dead_letter.sql",
        "orders/V202611010239__wish_item.sql",
        "orders/V202611011756__order_delivery_tracking_stopped_at.sql",
        "orders/V202611020031__order_item_seller_cancel.sql",
        "orders/V202611021347__wish_request.sql",
        "orders/V202611031010__order_child_deletion_tombstone.sql",
        "orders/V202611031100__order_outbox_quarantine.sql",
        "community/V202611020103__community_schema.sql",
        "community/V202611020132__community_replica_product.sql",
        "community/V202611021048__community_review_eligibility.sql",
        "community/V202611021140__community_review.sql",
        "community/V202611021458__community_favorite.sql",
        "community/V202611031200__community_dead_letter.sql");

    private PostgreSQLContainer postgres;
    private DriverManagerDataSource dataSource;
    private JdbcTemplate jdbc;

    @BeforeEach
    void migrateEmptyDatabase() {
        postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:18"));
        postgres.start();
        dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(),
            postgres.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        appFlyway().migrate();
    }

    @AfterEach
    void stopDatabase() {
        postgres.stop();
    }

    @Test
    @DisplayName("회원 서비스로 옮긴 마이그레이션의 이력 행은 지우고 검증을 통과한다")
    void dropsMovedMemberHistory() {
        insertHistory("202610301129", MOVED_MEMBER_SCRIPT);
        insertHistory("202611031204", MOVED_AUTH_SCRIPT);

        assertThatCode(() -> appFlyway().migrate()).doesNotThrowAnyException();
        assertThat(jdbc.queryForObject(
            "SELECT count(*) FROM public.flyway_schema_history WHERE script IN (?, ?)",
            Integer.class, MOVED_MEMBER_SCRIPT, MOVED_AUTH_SCRIPT)).isZero();
    }

    @Test
    @DisplayName("상품 서비스로 옮긴 마이그레이션의 이력 행은 지우고 검증을 통과한다")
    void dropsMovedProductHistory() {
        insertHistory("202610292246", MOVED_PRODUCT_OUTBOX_SCRIPT);
        insertHistory("202611040911", MOVED_PRODUCT_DROP_SEARCH_SCRIPT);
        insertHistory("202611041223", MOVED_PRODUCT_OUTBOX_IF_MISSING_SCRIPT);

        assertThatCode(() -> appFlyway().migrate()).doesNotThrowAnyException();
        assertThat(jdbc.queryForObject(
            "SELECT count(*) FROM public.flyway_schema_history WHERE script IN (?, ?, ?)",
            Integer.class, MOVED_PRODUCT_OUTBOX_SCRIPT, MOVED_PRODUCT_DROP_SEARCH_SCRIPT,
            MOVED_PRODUCT_OUTBOX_IF_MISSING_SCRIPT)).isZero();
    }

    @Test
    @DisplayName("주문 · 커뮤니티 서비스로 옮긴 마이그레이션의 이력 행은 지우고 검증을 통과한다")
    void dropsMovedOrderAndCommunityHistory() {
        MOVED_ORDER_COMMUNITY_SCRIPTS.forEach(script -> insertHistory(versionOf(script), script));

        assertThatCode(() -> appFlyway().migrate()).doesNotThrowAnyException();
        assertThat(jdbc.queryForObject(
            """
                SELECT count(*) FROM public.flyway_schema_history
                WHERE script LIKE 'orders/%' OR script LIKE 'community/%'
                """, Integer.class)).isZero();
    }

    @Test
    @DisplayName("이 앱이 적용한 마이그레이션 파일이 사라지면 검증에 실패한다")
    void failsWhenOwnMigrationIsMissing() {
        insertHistory("202610300000", "orders/V202610300000__removed.sql");

        assertThatThrownBy(() -> appFlyway().migrate()).isInstanceOf(FlywayValidateException.class);
    }

    // orders/V202610292033__order_outbox.sql → 202610292033
    private static String versionOf(String script) {
        return script.substring(script.indexOf("/V") + 2, script.indexOf("__"));
    }

    private void insertHistory(String version, String script) {
        jdbc.update("""
            INSERT INTO public.flyway_schema_history
                (installed_rank, version, description, type, script, installed_by, execution_time, success)
            SELECT max(installed_rank) + 1, ?, 'moved', 'SQL', ?, 'kidly', 0, true
            FROM public.flyway_schema_history
            """, version, script);
    }

    // 앱 application.yml의 spring.flyway 값 그대로
    private Flyway appFlyway() {
        var config = appConfig();
        return Flyway.configure()
            .dataSource(dataSource)
            .locations(config.getProperty("spring.flyway.locations"))
            .defaultSchema(config.getProperty("spring.flyway.default-schema"))
            .schemas(config.getProperty("spring.flyway.schemas").split(","))
            .baselineOnMigrate(Boolean.parseBoolean(config.getProperty("spring.flyway.baseline-on-migrate")))
            .baselineVersion(config.getProperty("spring.flyway.baseline-version"))
            .outOfOrder(Boolean.parseBoolean(config.getProperty("spring.flyway.out-of-order")))
            .load();
    }

    private static Properties appConfig() {
        var yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        return yaml.getObject();
    }
}
