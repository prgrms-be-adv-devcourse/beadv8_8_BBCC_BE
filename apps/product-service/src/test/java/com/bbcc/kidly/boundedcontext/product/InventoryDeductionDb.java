package com.bbcc.kidly.boundedcontext.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.bbcc.kidly.boundedcontext.product.domain.product.ProductOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import javax.sql.DataSource;
import org.assertj.core.groups.Tuple;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class InventoryDeductionDb {

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    InventoryDeductionDb(JdbcTemplate jdbcTemplate, DataSource dataSource) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
    }

    int availableQty(ProductOption option) {
        return jdbcTemplate.queryForObject(
            "select available_qty from product.inventory where product_option_id = ?", Integer.class, option.getId());
    }

    List<Tuple> records() {
        return jdbcTemplate.query(
            "select order_id, order_item_id, product_option_id, qty, status from product.inventory_deduction",
            (rs, rowNum) -> tuple(rs.getLong("order_id"), rs.getLong("order_item_id"),
                rs.getLong("product_option_id"), rs.getInt("qty"), rs.getString("status")));
    }

    List<Tuple> records(long orderId) {
        return jdbcTemplate.query(
            "select order_id, order_item_id, product_option_id, qty, status from product.inventory_deduction"
                + " where order_id = ?",
            (rs, rowNum) -> tuple(rs.getLong("order_id"), rs.getLong("order_item_id"),
                rs.getLong("product_option_id"), rs.getInt("qty"), rs.getString("status")), orderId);
    }

    long countByStatus(String status) {
        return jdbcTemplate.queryForObject(
            "select count(*) from product.inventory_deduction where status = ?", Long.class, status);
    }

    void stopSale(ProductOption option) {
        jdbcTemplate.update("update product.product set status = 'STOPPED'"
            + " where id = (select product_id from product.product_option where id = ?)", option.getId());
    }

    void restock(ProductOption option, int addedQty) {
        jdbcTemplate.update(
            "update product.inventory set available_qty = available_qty + ? where product_option_id = ?",
            addedQty, option.getId());
        jdbcTemplate.update("insert into product.inventory_adjustment_record"
            + " (product_option_id, delta_qty, reason, adjusted_by, created_at, updated_at)"
            + " values (?, ?, 'RESTOCK', ?, now(), now())", option.getId(), addedQty, ProductFixture.SELLER_ID);
    }

    HeldLock lockInventoryRow(ProductOption option) throws SQLException {
        Connection connection = openTransaction();
        try (PreparedStatement statement = connection.prepareStatement(
            "select id from product.inventory where product_option_id = ? for update")) {
            statement.setLong(1, option.getId());
            statement.executeQuery().close();
        }
        return new HeldLock(connection);
    }

    HeldLock lockProductRow(ProductOption option) throws SQLException {
        Connection connection = openTransaction();
        try (PreparedStatement statement = connection.prepareStatement("select id from product.product"
            + " where id = (select product_id from product.product_option where id = ?) for update")) {
            statement.setLong(1, option.getId());
            statement.executeQuery().close();
        }
        return new HeldLock(connection);
    }

    HeldLock holdUncommittedSaleStart(ProductOption option) throws SQLException {
        String changeStatus = "update product.product set status = ?"
            + " where id = (select product_id from product.product_option where id = ?)";
        jdbcTemplate.update(changeStatus, "SALE_PENDING", option.getId());
        Connection connection = openTransaction();
        try (PreparedStatement statement = connection.prepareStatement(changeStatus)) {
            statement.setString(1, "ON_SALE");
            statement.setLong(2, option.getId());
            statement.executeUpdate();
        }
        return new HeldLock(connection);
    }

    // 차감 기록 표를 SHARE 모드로 잠가 둔다. 조회는 지나가고 기록 INSERT만 기다린다
    HeldLock lockDeductionTable() throws SQLException {
        Connection connection = openTransaction();
        try (Statement statement = connection.createStatement()) {
            statement.execute("lock table product.inventory_deduction in share mode");
        }
        return new HeldLock(connection);
    }

    void awaitLockWaiters(int count) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            Long waiters = jdbcTemplate.queryForObject("select count(*) from pg_stat_activity"
                + " where wait_event_type = 'Lock' and datname = current_database()", Long.class);
            if (waiters != null && waiters >= count) {
                return;
            }
            Thread.sleep(20);
        }
        throw new IllegalStateException("잠금을 기다리는 연결이 " + count + "개가 되지 않았습니다");
    }

    private Connection openTransaction() throws SQLException {
        Connection connection = dataSource.getConnection();
        connection.setAutoCommit(false);
        return connection;
    }

    static final class HeldLock implements AutoCloseable {

        private final Connection connection;

        private HeldLock(Connection connection) {
            this.connection = connection;
        }

        @Override
        public void close() throws SQLException {
            try {
                connection.commit();
            } finally {
                connection.close();
            }
        }
    }

    void assertInvariants() {
        List<OptionStock> stocks = jdbcTemplate.query("""
            select i.product_option_id, i.available_qty,
                (select coalesce(sum(d.qty), 0) from product.inventory_deduction d
                 where d.product_option_id = i.product_option_id and d.status = 'DEDUCTED') as deducted_qty,
                (select coalesce(sum(a.delta_qty), 0) from product.inventory_adjustment_record a
                 where a.product_option_id = i.product_option_id) as adjusted_qty
            from product.inventory i
            """, (rs, rowNum) -> new OptionStock(rs.getLong("product_option_id"), rs.getInt("available_qty"),
            rs.getInt("deducted_qty"), rs.getInt("adjusted_qty")));
        for (OptionStock stock : stocks) {
            assertThat(stock.availableQty()).as("INV-01 옵션 %s의 판매 가능 수량", stock.optionId()).isNotNegative();
            assertThat(stock.availableQty() + stock.deductedQty())
                .as("INV-02 옵션 %s의 판매 가능 수량 + 차감 수량", stock.optionId()).isEqualTo(stock.adjustedQty());
        }
        assertThat(jdbcTemplate.queryForList("select order_item_id from product.inventory_deduction"
            + " group by order_item_id having count(*) > 1", Long.class)).as("INV-10 주문상품마다 기록 한 줄").isEmpty();
    }

    private record OptionStock(long optionId, int availableQty, int deductedQty, int adjustedQty) {
    }
}
