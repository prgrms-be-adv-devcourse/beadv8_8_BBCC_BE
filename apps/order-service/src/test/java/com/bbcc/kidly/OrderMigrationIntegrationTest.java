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
 * 서비스가 처음 뜰 때 주문 테이블이 이미 있는 DB에서 서비스 Flyway가 baseline을 잡고 나서의 스키마를 본다.
 * kidly-app이 주문을 가지고 있던 동안 쓰던 DB에는 주문 마이그레이션을 모두 적용해 두었다.
 * baseline-version은 서비스가 처음 뜰 때 한 번만 쓰이므로 고정해 두고, 그 뒤에 더한 주문 마이그레이션은 서비스가 적용한다.
 * 그래서 이 DB에 서비스 Flyway를 돌려도 스키마가 바뀌지 않아야 한다.
 * 주문 분리 당시(#826) kidly-app은 자기 폴더의 주문 파일(init의 orders 구문 · 옛 주문 파일 5개)까지만 적용했다.
 * 서비스는 그 뒤 baseline을 잡아 주문 모듈 마이그레이션을 건너뛰므로, 그런 DB에서도 모든 주문 마이그레이션을 적용한 DB와 스키마가 같아야 한다
 * (보강 마이그레이션 V202611041730).
 * 서비스가 뜨기 전의 DB를 만들어야 하므로 스프링 컨텍스트(@IntegrationTest) 없이 Flyway를 직접 돌린다.
 */
@Tag("integration")
class OrderMigrationIntegrationTest {

    private static final String BASELINE_VERSION = "202611031100";
    // kidly-app 폴더에 있던 마지막 주문 마이그레이션(이벤트 처리 실패 보관)
    private static final String LAST_KIDLY_APP_MODULE_VERSION = "202610191003";
    // 장바구니 주인 마이그레이션(#998) 바로 앞 버전과 그 파일
    private static final String CART_OWNER_VERSION_BEFORE = "202611061700";
    private static final String CART_OWNER_MIGRATION =
        "db/migration/orders/V202611062000__cart_owner_reservation.sql";
    // 조르기 구매 요청 마이그레이션(#999) 바로 앞 버전과 그 파일
    private static final String WISH_PURCHASE_VERSION_BEFORE = "202611062000";
    private static final String WISH_PURCHASE_MIGRATION =
        "db/migration/orders/V202611062100__wish_purchase_request.sql";
    // 칸 예약 결제 정리 시각 마이그레이션(#999) 바로 앞 버전과 그 파일
    private static final String RESERVATION_PURCHASED_VERSION_BEFORE = "202611062100";
    private static final String RESERVATION_PURCHASED_MIGRATION =
        "db/migration/orders/V202611062150__cart_reservation_purchased_at.sql";
    // 조르기 만료 마이그레이션(#1008) 바로 앞 버전과 그 파일
    private static final String WISH_EXPIRY_VERSION_BEFORE = "202611062150";
    private static final String WISH_EXPIRY_MIGRATION = "db/migration/orders/V202611062200__wish_request_expiry.sql";
    // 장바구니 예약 결제 가능 기한 마이그레이션(#1008) 바로 앞 버전과 그 파일
    private static final String RESERVATION_VALID_UNTIL_VERSION_BEFORE = "202611062200";
    private static final String RESERVATION_VALID_UNTIL_MIGRATION =
        "db/migration/orders/V202611062400__cart_reservation_valid_until.sql";
    // 주문상품 판매자 인덱스 마이그레이션(#1120) 파일
    private static final String ORDER_ITEM_SELLER_INDEX_MIGRATION =
        "db/migration/orders/V202611101300__order_item_seller_index.sql";
    // 판매 불가 판매자 취소 사유 마이그레이션(#1121) 파일
    private static final String SELLER_UNAVAILABLE_MIGRATION =
        "db/migration/orders/V202611101400__seller_unavailable_cancel_reason.sql";

    @Test
    @DisplayName("kidly-app이 주문 마이그레이션을 모두 적용한 DB에서는 baseline을 잡고 스키마를 바꾸지 않는다")
    void keepsSchemaWhenKidlyAppAppliedAllMigrations() {
        try (var postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:18"))) {
            postgres.start();
            var dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword());
            var jdbc = new JdbcTemplate(dataSource);
            createOrderTablesLikeKidlyApp(dataSource, jdbc, null);
            var before = schema(jdbc);

            serviceFlyway(dataSource).load().migrate();

            assertThat(schema(jdbc)).isEqualTo(before);
            assertThat(historyTypes(jdbc)).first().isEqualTo("BASELINE " + BASELINE_VERSION);
        }
    }

    @Test
    @DisplayName("kidly-app 폴더의 주문 파일까지만 있는 DB에서 baseline을 잡아도 모든 주문 마이그레이션을 적용한 DB와 스키마가 같다")
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
            createOrderTablesLikeKidlyApp(fullDataSource, new JdbcTemplate(fullDataSource), null);
            createOrderTablesLikeKidlyApp(dataSource, jdbc, LAST_KIDLY_APP_MODULE_VERSION);

            serviceFlyway(dataSource).load().migrate();

            assertThat(schema(jdbc)).isEqualTo(schema(new JdbcTemplate(fullDataSource)));
            assertThat(historyTypes(jdbc)).first().isEqualTo("BASELINE " + BASELINE_VERSION);
        }
    }

    @Test
    @DisplayName("장바구니 주인 마이그레이션(#998): 기존 칸은 MEMBER + member_id로 옮기고, 같은 파일을 다시 적용해도 오류 · 변화가 없다")
    void cartOwnerMigrationMovesRowsAndIsRepeatable() throws Exception {
        try (var postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:18"))) {
            postgres.start();
            var dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword());
            var jdbc = new JdbcTemplate(dataSource);
            createOrderTablesLikeKidlyApp(dataSource, jdbc, CART_OWNER_VERSION_BEFORE);
            jdbc.update("INSERT INTO orders.cart_item (member_id, option_id, child_id, quantity, version, created_at,"
                + " updated_at) VALUES (7, 1, null, 2, 0, now(), now()), (7, 1, 3, 1, 0, now(), now())");

            serviceFlyway(dataSource).load().migrate();
            var migrated = jdbc.queryForList("SELECT owner_type || ':' || owner_id || ':' || member_id"
                + " FROM orders.cart_item ORDER BY id", String.class);
            String sql = new String(new ClassPathResource(CART_OWNER_MIGRATION).getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);
            var before = schema(jdbc);
            jdbc.execute(sql);

            assertThat(migrated).containsExactly("MEMBER:7:7", "MEMBER:7:7");
            assertThat(schema(jdbc)).isEqualTo(before);
            assertThat(jdbc.queryForList("SELECT owner_type || ':' || owner_id FROM orders.cart_item ORDER BY id",
                String.class)).containsExactly("MEMBER:7", "MEMBER:7");
        }
    }

    @Test
    @DisplayName("조르기 구매 요청 마이그레이션(#999): 진행 중 조르기는 CANCELLED(SYSTEM), 바로 결제가 결제된 것은 PAID, 거절 · 철회는 "
        + "결정한 사람 · 원인으로 옮기고 공용 장바구니 표 · 줄의 공용 장바구니 칸을 지운다. 같은 파일을 다시 적용해도 오류 · 변화가 없고 "
        + "새 모델의 대기 · 결제 완료 조르기는 그대로다")
    void wishPurchaseMigrationMovesRowsAndIsRepeatable() throws Exception {
        try (var postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:18"))) {
            postgres.start();
            var dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword());
            var jdbc = new JdbcTemplate(dataSource);
            createOrderTablesLikeKidlyApp(dataSource, jdbc, WISH_PURCHASE_VERSION_BEFORE);
            jdbc.update("""
                INSERT INTO orders.wish_item (id, child_id, option_id, quantity, added_by_type, added_by_id, status,
                    request_id, version, created_at, updated_at)
                VALUES (1, 3, 1, 1, 'CHILD', 3, 'ACTIVE', 1, 0, now(), now())""");
            jdbc.update("""
                INSERT INTO orders.wish_request (id, child_id, requested_by_type, requested_by_id, status, checkout,
                    order_id, order_result, reject_reason, decided_by_id, decided_at, cancelled_at, version,
                    created_at, updated_at)
                VALUES (1, 3, 'CHILD', 3, 'REQUESTED', null, null, null, null, null, null, null, 0, now(), now()),
                       (2, 3, 'PARENT', 7, 'APPROVED', 'ORDER', 55, 'PAID', null, 7, '2026-11-01 10:00', null, 0,
                        now(), now()),
                       (3, 3, 'CHILD', 3, 'APPROVED', 'CART', null, null, null, 7, '2026-11-01 11:00', null, 0,
                        now(), now()),
                       (4, 3, 'CHILD', 3, 'REJECTED', null, null, null, 'NEXT_TIME', 7, '2026-11-01 12:00', null, 0,
                        now(), now()),
                       (5, 3, 'CHILD', 3, 'CANCELLED', null, null, null, null, null, null, '2026-11-01 13:00', 0,
                        now(), now()),
                       (6, 3, 'CHILD', 3, 'APPROVED', 'ORDER', 56, null, null, 7, '2026-11-01 14:00', null, 0,
                        now(), now())""");
            jdbc.update("""
                INSERT INTO orders.wish_request_line (request_id, wish_item_id, option_id, requested_quantity,
                    approved_quantity)
                VALUES (1, 1, 1, 1, null), (2, 1, 1, 2, 1)""");

            serviceFlyway(dataSource).load().migrate();
            var migrated = wishRows(jdbc);
            String sql = new String(new ClassPathResource(WISH_PURCHASE_MIGRATION).getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);
            // 새 모델로 생긴 대기 · 결제 완료 조르기는 다시 적용돼도 그대로다
            jdbc.update("""
                INSERT INTO orders.wish_request (id, type, child_id, requested_by_type, requested_by_id, status,
                    payer_type, payer_id, paid_at, version, created_at, updated_at)
                VALUES (7, 'WISH', 3, 'CHILD', 3, 'REQUESTED', null, null, null, 0, now(), now()),
                       (8, 'WISH', 3, 'CHILD', 3, 'PAID', 'MEMBER', 7, now(), 0, now(), now())""");
            var withNewRows = wishRows(jdbc);
            var before = schema(jdbc);
            jdbc.execute(sql);

            assertThat(withNewRows).endsWith(
                "7 WISH REQUESTED null CHILD:3 payer=null:null rejecter=null:null",
                "8 WISH PAID null CHILD:3 payer=MEMBER:7 rejecter=null:null");
            assertThat(migrated).containsExactly(
                "1 WISH CANCELLED SYSTEM CHILD:3 payer=null:null rejecter=null:null",
                "2 WISH PAID null MEMBER:7 payer=MEMBER:7 rejecter=null:null",
                "3 WISH CANCELLED SYSTEM CHILD:3 payer=null:null rejecter=null:null",
                "4 WISH REJECTED null CHILD:3 payer=null:null rejecter=MEMBER:7",
                "5 WISH CANCELLED DIRECT CHILD:3 payer=null:null rejecter=null:null",
                "6 WISH CANCELLED SYSTEM CHILD:3 payer=null:null rejecter=null:null");
            assertThat(schema(jdbc)).isEqualTo(before);
            assertThat(wishRows(jdbc)).isEqualTo(withNewRows);
            assertThat(jdbc.queryForObject("SELECT paid_at FROM orders.wish_request WHERE id = 2", String.class))
                .startsWith("2026-11-01 10:00");
            assertThat(jdbc.queryForObject("SELECT to_regclass('orders.wish_item') IS NULL", Boolean.class)).isTrue();
            assertThat(jdbc.queryForList("SELECT column_name FROM information_schema.columns WHERE table_schema = "
                + "'orders' AND table_name = 'wish_request_line' ORDER BY ordinal_position", String.class))
                .containsExactly("id", "request_id", "option_id", "requested_quantity", "cart_item_id", "product_id");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM orders.wish_request_line", Long.class)).isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT to_regclass('orders.wish_purchase') IS NOT NULL", Boolean.class))
                .isTrue();
        }
    }

    @Test
    @DisplayName("칸 예약 결제 정리 시각 · 버전 마이그레이션(#999): 기존 예약 행은 시각 null · 버전 0이고, 같은 파일을 다시 적용해도 오류 · 변화가 없다")
    void reservationPurchasedMigrationIsRepeatable() throws Exception {
        try (var postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:18"))) {
            postgres.start();
            var dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword());
            var jdbc = new JdbcTemplate(dataSource);
            createOrderTablesLikeKidlyApp(dataSource, jdbc, RESERVATION_PURCHASED_VERSION_BEFORE);
            jdbc.update("INSERT INTO orders.cart_reservation (reservation_id, payer_type, payer_id, created_at,"
                + " updated_at) VALUES ('wish-1', 'MEMBER', 7, now(), now())");

            serviceFlyway(dataSource).load().migrate();
            jdbc.update("INSERT INTO orders.cart_reservation (reservation_id, payer_type, payer_id, purchased_at,"
                + " version, created_at, updated_at) VALUES ('wish-2', 'MEMBER', 7, '2026-11-06 22:00:00', 3, now(),"
                + " now())");
            var before = schema(jdbc);
            String sql = new String(new ClassPathResource(RESERVATION_PURCHASED_MIGRATION).getInputStream()
                .readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            jdbc.execute(sql);

            assertThat(schema(jdbc)).isEqualTo(before)
                .contains("cart_reservation.purchased_at timestamp without time zone YES",
                    "cart_reservation.version bigint NO");
            assertThat(jdbc.queryForList("SELECT reservation_id || ':' || coalesce(purchased_at::text, 'null') || ':'"
                + " || version FROM orders.cart_reservation ORDER BY id", String.class))
                .containsExactly("wish-1:null:0", "wish-2:2026-11-06 22:00:00:3");
        }
    }

    private static List<String> wishRows(JdbcTemplate jdbc) {
        return jdbc.queryForList("""
            SELECT id || ' ' || type || ' ' || status || ' ' || coalesce(cancel_reason, 'null') || ' '
                || requested_by_type || ':' || requested_by_id
                || ' payer=' || coalesce(payer_type, 'null') || ':' || coalesce(payer_id::text, 'null')
                || ' rejecter=' || coalesce(rejected_by_type, 'null') || ':' || coalesce(rejected_by_id::text, 'null')
            FROM orders.wish_request ORDER BY id""", String.class);
    }

    @Test
    @DisplayName("조르기 만료 마이그레이션(#1008): 상태에 EXPIRED · expired_at이 생기고, 같은 파일을 다시 적용해도 오류 · 변화가 없다")
    void wishExpiryMigrationIsRepeatable() throws Exception {
        try (var postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:18"))) {
            postgres.start();
            var dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword());
            var jdbc = new JdbcTemplate(dataSource);
            createOrderTablesLikeKidlyApp(dataSource, jdbc, WISH_EXPIRY_VERSION_BEFORE);
            jdbc.update("""
                INSERT INTO orders.wish_request (id, type, child_id, requested_by_type, requested_by_id, status,
                    version, created_at, updated_at)
                VALUES (1, 'WISH', 3, 'CHILD', 3, 'REQUESTED', 0, now(), now())""");

            serviceFlyway(dataSource).load().migrate();
            jdbc.update("UPDATE orders.wish_request SET status = 'EXPIRED', expired_at = now() WHERE id = 1");
            String sql = new String(new ClassPathResource(WISH_EXPIRY_MIGRATION).getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);
            var before = schema(jdbc);
            jdbc.execute(sql);

            assertThat(schema(jdbc)).isEqualTo(before);
            assertThat(jdbc.queryForObject("SELECT status FROM orders.wish_request WHERE id = 1", String.class))
                .isEqualTo("EXPIRED");
        }
    }

    @Test
    @DisplayName("장바구니 예약 결제 가능 기한 마이그레이션(#1008): 기존 예약 행은 기한 null이고, 같은 파일을 다시 적용해도 오류 · 변화가 없다")
    void reservationValidUntilMigrationIsRepeatable() throws Exception {
        try (var postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:18"))) {
            postgres.start();
            var dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword());
            var jdbc = new JdbcTemplate(dataSource);
            createOrderTablesLikeKidlyApp(dataSource, jdbc, RESERVATION_VALID_UNTIL_VERSION_BEFORE);
            jdbc.update("INSERT INTO orders.cart_reservation (reservation_id, payer_type, payer_id, created_at,"
                + " updated_at) VALUES ('wish-1', 'MEMBER', 7, now(), now())");

            serviceFlyway(dataSource).load().migrate();
            jdbc.update("INSERT INTO orders.cart_reservation (reservation_id, payer_type, payer_id, valid_until,"
                + " created_at, updated_at) VALUES ('wish-2', 'MEMBER', 7, '2026-11-13 22:00:00', now(), now())");
            var before = schema(jdbc);
            String sql = new String(new ClassPathResource(RESERVATION_VALID_UNTIL_MIGRATION).getInputStream()
                .readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            jdbc.execute(sql);

            assertThat(schema(jdbc)).isEqualTo(before)
                .contains("cart_reservation.valid_until timestamp without time zone YES");
            assertThat(jdbc.queryForList("SELECT reservation_id || ':' || coalesce(valid_until::text, 'null')"
                + " FROM orders.cart_reservation ORDER BY id", String.class))
                .containsExactly("wish-1:null", "wish-2:2026-11-13 22:00:00");
        }
    }

    @Test
    @DisplayName("주문상품 판매자 인덱스 마이그레이션(#1120): (seller_id, status) 인덱스가 생기고, 같은 파일을 다시 적용해도 오류 · 변화가 없다")
    void orderItemSellerIndexMigrationIsRepeatable() throws Exception {
        try (var postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:18"))) {
            postgres.start();
            var dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword());
            var jdbc = new JdbcTemplate(dataSource);
            serviceFlyway(dataSource).load().migrate();
            var before = schema(jdbc);
            String sql = new String(new ClassPathResource(ORDER_ITEM_SELLER_INDEX_MIGRATION).getInputStream()
                .readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            jdbc.execute(sql);

            assertThat(schema(jdbc)).isEqualTo(before).contains("CREATE INDEX idx_order_item_seller_status ON "
                + "orders.order_item USING btree (seller_id, status)");
        }
    }

    @Test
    @DisplayName("판매 불가 판매자 취소 사유 마이그레이션(#1121): 세 사유 칸이 SELLER_UNAVAILABLE을 받고, 같은 파일을 다시 적용해도 "
        + "오류 · 변화가 없으며 예전 사유 값도 그대로 받는다")
    void sellerUnavailableReasonMigrationIsRepeatable() throws Exception {
        try (var postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:18"))) {
            postgres.start();
            var dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword());
            var jdbc = new JdbcTemplate(dataSource);
            serviceFlyway(dataSource).load().migrate();
            var before = schema(jdbc);
            String sql = new String(new ClassPathResource(SELLER_UNAVAILABLE_MIGRATION).getInputStream()
                .readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            jdbc.execute(sql);

            assertThat(schema(jdbc)).isEqualTo(before);
            var definitions = jdbc.queryForList("SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname IN "
                + "('orders_cancel_reason_check', 'order_item_cancel_reason_check', "
                + "'order_item_cancellation_reason_check')", String.class);
            assertThat(definitions).hasSize(3).allSatisfy(definition -> assertThat(definition)
                .contains("SELLER_UNAVAILABLE", "OUT_OF_STOCK"));
        }
    }

    // kidly-app이 적용한 주문 마이그레이션(init의 orders 구문 · 옛 주문 파일 · 도메인 모듈 파일)은 이 서비스의 것과 같다.
    // target 버전까지(null이면 모두) 적용하고 그 이력은 지워 kidly-app이 만든 상태로 둔다
    private static void createOrderTablesLikeKidlyApp(DriverManagerDataSource dataSource, JdbcTemplate jdbc,
        String target) {
        var flyway = Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .defaultSchema("orders")
            .schemas("orders")
            .table("kidly_app_init_history");
        if (target != null) {
            flyway.target(target);
        }
        flyway.load().migrate();
        jdbc.execute("DROP TABLE orders.kidly_app_init_history");
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

    // 주문 스키마의 칸(표 · 칸 · 타입 · null 허용), 제약 이름, 인덱스 정의. 서비스 이력 테이블은 뺀다
    private static List<String> schema(JdbcTemplate jdbc) {
        var columns = jdbc.queryForList("""
            SELECT table_name || '.' || column_name || ' ' || data_type || ' ' || is_nullable
            FROM information_schema.columns
            WHERE table_schema = 'orders' AND table_name NOT LIKE 'flyway_schema_history%'
            ORDER BY 1
            """, String.class);
        var constraints = jdbc.queryForList("""
            SELECT conname FROM pg_constraint
            WHERE connamespace = 'orders'::regnamespace AND conname NOT LIKE 'flyway_schema_history%'
            ORDER BY 1
            """, String.class);
        var indexes = jdbc.queryForList("""
            SELECT indexdef FROM pg_indexes
            WHERE schemaname = 'orders' AND tablename NOT LIKE 'flyway_schema_history%'
            ORDER BY 1
            """, String.class);
        return Stream.of(columns, constraints, indexes).flatMap(List::stream).toList();
    }

    private static List<String> historyTypes(JdbcTemplate jdbc) {
        return jdbc.queryForList("""
            SELECT type || ' ' || version FROM orders.flyway_schema_history_order
            WHERE version IS NOT NULL ORDER BY installed_rank
            """, String.class);
    }

}
